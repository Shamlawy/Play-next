# Perfect run · fix helper "plumbing" (E, F-reporter, G, P, R, V-comments)

Branch `worktree-agent-a64f780e04927911c`, code head `b0f5a0a` (on main 697c8cd); this file is the commit after it. Not merged, no PR.

Files touched: report/Reporter.kt, report/Report.kt, report/FrameGate.kt, update/Updater.kt, update/ReleaseFeed.kt, update/Receivers.kt, net/Net.kt; tests ReporterTest, UpdaterTest, FrameRulesTest (new), RoutesTest, ReleaseFeedTest, ReportRulesTest.

How "failed before" was shown: test seams were added first with the old behaviour kept (Reporter.send / internal enqueue / ranOnce(code); Updater.fetch / Updater.attempt split out unchanged), the new tests were run against that: 10 of the 10 new Reporter/Updater/Routes tests failed, each for the reason below. Then the fixes went in and they pass.

## E · Reporter (safety 3/4/5, bugs 7, basics 2)
- One flush at a time (`Mutex`); the sent ones leave the in-memory queue by identity (`===`), not `drop(batch.size)`.
- `flushSoon` cancels only the pause: once it's over, the flush is its own job (`scope.launch` from inside the wait), so a newer report never cancels a batch on its way. CancellationException is rethrown, any other send error = not sent.
- The queue is held in memory (read from its file once) and written on `Dispatchers.IO`: `report()` builds on the caller (scrub + sig) and queues/writes in the Reporter scope; `user()` and `flush()` run under `withContext(IO)`; `adopt()` reads the crash files on IO. `firstToday` has its own lock, so a report built on the main thread never waits on a file write.
- Flush at every start: `adopt()` (already called on every start in PlayNextApp.onCreate, safe mode too) ends with `flushSoon(3000)`. No change needed in PlayNextApp.kt.
- The ran report: `ReportRules.sig(..., apart = "v<code>")` (new optional param, empty = the old signature for every other report), and `ranV` is set after queueing.
- Tests (all failed before): `ReporterTest.aBatchOnItsWayIsNeverSentTwice` (before: `[s1, s1, s2]`), `reportsQueuedWhileABatchIsOutAreKept` (before: 20 left, 5 unsent lost; after 25), `everyStartSendsWhatTheLastRunLeft` (before: nothing sent), `twoReleasesOnOneDayBothSayTheyRan` (before: 1 queued). Plus `ReportRulesTest.eachVersionsRanReportIsItsOwn`.

## F · Reporter part (bugs 6)
- Queue writes are in `runCatching`; the queue stays in memory and the next change writes it again.
- Test: `ReporterTest.aFullPhoneDoesntCrashAndTheReportStillGoes` (a directory where queue.tmp goes; before: FileNotFoundException thrown out of enqueue).

## G · Updater (safety 2/8, speed 3)
- `fetch(dir, rel, sha)`: a `.part` at least the APK's size goes straight to the SHA check (no `Range: bytes=<size>-`); a mismatch deletes it and downloads from 0; a 416 on a resumed request also goes to the check.
- `Net.call(make: () -> Request, read)`: the request (and its Range) is made per try, so a retry after a mid-body drop asks from where the part ends.
- SHA check + swap in `withContext(NonCancellable)` (`keep()`).
- `run()` is `withContext(Dispatchers.IO) { busy.withLock { attempt { … } } }`: the release list decode and the APK verify are off Main. `attempt` catches every Exception (rethrows CancellationException; a cancel mid-download puts the state back to Idle instead of leaving "Downloading N%") → `Failed` + a report: a non-IO error (a bug) at once, an IOException when the same failure repeats 3 runs in a row (once per failure, `updFail`/`updFails` prefs, cleared on success; no-signal errors — UnknownHost, Connect, timeouts — never).
- Only the picked release is kept for the next 304 (`relPick`, `ReleaseFeed.keep/kept`, a few hundred bytes; still-newer check against the current version); stored only after the list parsed; the old `relBody` key (and its ETag) is removed once. A list that doesn't parse falls back to the text asset.
- Tests: `UpdaterTest.aFinishedPartGoesStraightToTheCheck` (before: `IOException: download 416`), `aDroppedLineResumesWhereItStopped` (before: SHA mismatch from duplicated bytes), `anUnexpectedErrorIsAFailureNotACrash` (before: SerializationException thrown), `aFailureThatKeepsComingBackIsToldOnce` (before: 0 reports). `ReleaseFeedTest.theKeptPickIsSmallAndStaysNewer`. NonCancellable and the IO dispatch are by code trace.

## P · FrameGate (speed 6, speed 7 part)
- Judges `FrameDataApi31.frameDurationTotalNanos` (falls back to the UI time on older data); the note adds "the UI thread's part of the worst: N ms".
- 800 ms after the window changes size (a decorView layout listener comparing old/new size: MainActivity handles fold/turn itself, no recreate) frames don't count.
- The burst is filed from a background coroutine (`Dispatchers.Default`), not the main handler; Reporter queues on IO.
- Rules split into `FrameRules.counts(totalNs, now, start, resized, screen)`; test `FrameRulesTest` (3 cases). It's a new seam, so it couldn't fail before; JankStats itself and the layout listener are code trace only.

## R · write switch and redirects (safety 6)
- `Net.Guard` is also added with `addNetworkInterceptor`, so a 307/308 that re-POSTs is checked again and refused (WriteBlocked).
- Test: `RoutesTest.aRedirectCantTurnAReportIntoAWrite` (MockWebServer: `/bug` → 307 `/vault/put`; before: the call went through, after: WriteBlocked and 1 request).

## V · comments
- Updater.kt (`needsTap`, `canInstall`) and Receivers.kt (ABORTED) now say Settings, not Home.

## For the coordinator (outside my files)
- No call to add in PlayNextApp.kt: the existing `Reporter.adopt(CrashGuard.pendingFiles())` is the every-start flush.
- `Reporter.build` gained a last optional param `apart`; CrashGuard's named-argument call is unaffected.
- `Reporter.report()` now returns before the report is in the queue (it's queued on IO a moment later). Safe mode: SafeModeActivity's immediate `flush()` may run before adopted crash reports are queued; adopt's own flush 3 s later sends them.
- mockwebserver3 is used now (RoutesTest): leftovers 8's "dead test deps" should keep it; coroutines-test is still unused.
- Settings shows a Failed state's `why`: for a non-IO error it reads "something went wrong (<Exception>: <message>)".

## Final run
`tools/gradle-retry.sh :app:testDebugUnitTest :app:lintRelease` on b0f5a0a: 109 tests (94 + 15 new), 0 failed, 4 skipped (GoldenTest, which needs the private golden data, skipped in this container), lintRelease 0 errors.
