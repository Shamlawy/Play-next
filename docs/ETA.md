# Time estimates

Every "⏱ About N min" Claude gives, against the time it really took (the owner, Oct 23: "do monitor that and see how
well you do and learn"). Written by `node tools/eta.mjs` (the learn mod starts and ends entries by itself); × is
took ÷ said, so under 1 means faster than promised. The first message of each chat gets the calibration per kind.

| id | started (UAE) | kind | said | took | × | what |
|---|---|---|---|---|---|---|
| E1 | 2026-10-23 3:47 PM | research | 25 min | 9 min | 0.36 | Gap check of the Android plan with 4 helpers | <!-- at=2026-10-23T11:47:00.000Z -->
| E2 | 2026-10-23 10:57 PM | build | 40 min | 5 min | 0.13 | Estimate tracker (tools/eta.mjs + the learn mod) | <!-- at=2026-10-23T18:57:00.000Z note=2_follow-up_fixes_from_second-look -->
| E3 | 2026-10-26 1:03 PM | kit | 2 h | 52 min | 0.43 | I'll write the rest of ▢ 1, build and test it here, then open and merge the PR. ▢ be done around 3:05 PM UAE time. | <!-- at=2026-10-26T09:03:21.584Z bg=1 -->
| E4 | 2026-10-26 1:23 PM | ci | 12 min | 19 min | 1.58 | PR #650 checks (another chat; merged 1:42 PM) | <!-- at=2026-10-26T09:23:00.000Z -->
| E5 | 2026-10-26 1:24 PM | build | 60 min | 31 min | 0.52 | Rest of Android Piece 1 on the PC, built and pushed (a duplicate of #650) | <!-- at=2026-10-26T09:24:00.000Z -->
| E6 | 2026-10-27 10:25 AM | release | 60 min | 54 min | 0.90 | android-v1: cert PR #654, release check fix #655, release run by hand (this cloud chat) | <!-- at=2026-10-27T06:25:00.000Z -->
| E7 | 2026-10-27 2:18 PM | release | 60 min | 29 min | 0.48 | I'll finish the screenshot tests, CI, the release checks, then merge android-v6 and send you what to try. | <!-- at=2026-10-27T10:18:28.661Z -->
| E8 | 2026-10-27 3:24 PM | ci | 15 min | 14 min | 0.93 | Confirm android-v6's release and merge #664 (handoff chat) | <!-- at=2026-10-27T11:24:00.000Z -->
| E9 | 2026-10-28 8:53 AM | kit | 40 min | 13 min | 0.33 | I'll check that android-v8 published, fix the two review notes from #666 in a small PR, test it, merge it, and send a short summary at the e | <!-- at=2026-10-28T04:53:21.691Z -->
| E10 | 2026-10-28 10:36 AM | release | 50 min | 38 min | 0.61 | I'll change back so it walks the path you took, release it as android-v9 and tell you what to try. | <!-- at=2026-10-28T06:36:21.963Z more=12 note=review_found_a_stuck_page;_fixed_before_the_merge -->
| E11 | 2026-10-29 8:52 AM | ci | 25 min | 68 min | 2.72 | CI on PR #671 is running now, should be done around 9:20 AM UAE time. I'm subscribed to it, so I'll get a ping when it finishes. Then I'll f | <!-- at=2026-10-29T04:52:37.310Z bg=1 -->
| E13 | 2026-10-29 9:59 AM | release | 60 min | 2.2 h | 2.20 | PR #672's checks (~25 min, started 9:58 AM), then the release run by hand (~30 min). ▢ the end I'll send the release result and the approva | <!-- at=2026-10-29T05:59:48.723Z -->
| E14 | 2026-11-02 12:10 PM | plan | 3 min | 3 min | 1.00 | I'll read the plan, the log and what's open, then send where we are and a week plan. | <!-- at=2026-11-02T08:10:00.347Z -->
| E15 | 2026-11-02 12:22 PM | plan | 10 min | 2 min | 0.20 | I'll record your approval and put together the Phase 1 (Queue) ▢, a plain list for you to tick. ▢ gets built until you answer it. | <!-- at=2026-11-02T08:22:36.934Z -->
| E16 | 2026-11-02 12:30 PM | research | 30 min | 26 min | 0.87 | for the ▢: two helpers read the web Queue, then I check their lists and save them. ▢ be done around 1:35 PM UAE. ▢ that I'll chec | <!-- at=2026-11-02T08:30:21.541Z bg=1 -->
| E17 | 2026-11-02 1:31 PM | release | 15 min | 36 min | 2.40 | until the checks finish. Then I merge and tell you what to try. | <!-- at=2026-11-02T09:31:13.697Z bg=1 note=merge-ready -->
| E18 | 2026-11-03 10:37 AM | release | 30 min | 28 min | 0.93 | I'll confirm android-v11's release (its run should finish around 10:55 AM UAE time), then bring main into the piece 2 branch, open its PR an | <!-- at=2026-11-03T06:37:44.781Z bg=1 -->
| E19 | 2026-11-03 12:34 PM | release | 15 min | 11 min | 0.73 | I'll wait for the checks, merge it, and tell you when android-v12 is out. | <!-- at=2026-11-03T08:34:37.123Z bg=1 note=merged -->
| E20 | 2026-11-03 1:19 PM | kit | 60 min | 89 min | 1.48 | I'll build the lists sheet (▢) and the slider fix, test them, release android-v13 and send you what to try. ▢ be done around 2:20 PM ▢ | <!-- at=2026-11-03T09:19:10.707Z bg=1 -->
| E21 | 2026-11-03 2:50 PM | release | 10 min | 18 min | 1.80 | the re-run started at 2:48 PM and should finish around 2:58 PM UAE time. ▢ it passes, I'll merge android-v13 and tell you when it's out. | <!-- at=2026-11-03T10:50:00.780Z bg=1 -->
| E22 | 2026-11-03 3:14 PM | build | 75 min | 70 min | 0.61 | I'll build "add games to a list", test it, release it as android-v14, and send you screenshots and what to try. | <!-- at=2026-11-03T11:14:12.240Z bg=1 more=40 -->
| E23 | 2026-11-03 4:45 PM | kit | 1.5 h | 40 min | 0.44 | I'll build the open Queue card on Android with your answer (a double tap mid-turn sends it back), test it, release it as android-v15, and se | <!-- at=2026-11-03T12:45:09.881Z -->
| E24 | 2026-11-05 11:50 AM | release | 2 h | 2 h | 1.00 | I'll fix the card's closing animation, build hold-and-drag in the Queue, and run the speed gate. ▢ the end you get a new Android version pl | <!-- at=2026-11-05T07:50:30.172Z -->
| E25 | 2026-11-05 12:58 PM | ci | 75 min | 14 min | 0.19 | (scaled for builds + CI): ▢ install, local ▢ checks, the new test, then PR → android-check → merge → android-release. I'll end with a | <!-- at=2026-11-05T08:58:14.005Z -->
| E26 | 2026-11-05 2:05 PM | release | 1.5 h | 75 min | 0.83 | I'll add the tests, release android-v17, merge it, and send you what to try. | <!-- at=2026-11-05T10:05:44.139Z -->
| E27 | 2026-11-05 3:23 PM | release | 75 min | 87 min | 1.16 | I'll fix all three, test them, release android-v18, and send you what to try. | <!-- at=2026-11-05T11:23:59.034Z note=ended_at_the_release_(4:50_PM),_not_at_the_handoff -->
| E28 | 2026-11-05 4:15 PM | release | 45 min | 35 min | 0.78 | I'll merge android-v18 once its two Android checks pass, then tell you when it reaches your phone. | <!-- at=2026-11-05T12:15:18.631Z -->
| E30 | 2026-11-06 1:59 PM | video | 1.5 h | 12 min | 0.13 | I'll build both fixes, run the kit on every tab at folded and unfolded widths, and send the PR link with screenshots. | <!-- at=2026-11-06T09:59:34.812Z -->
| E31 | 2026-11-06 2:13 PM | build | 45 min | 24 min | 0.53 | fix the 4 tests, re-record screenshots, run the checks, a review, then open the PR and send you the link. | <!-- at=2026-11-06T10:13:38.896Z -->
| E32 | 2026-11-06 2:48 PM | ci | 40 min | 40 min | 1.00 | I'll put Your lists in the ▢ menu (the suggested home), test it, wait for ▢ checks, merge, and tell you when it reaches your phone | <!-- at=2026-11-06T10:48:53.253Z -->
| E33 | 2026-11-06 7:20 PM | ci | 60 min | 10 min | 0.17 | including ▢ checks and the release build. I'll send before/after pictures and tell you when it's out. | <!-- at=2026-11-06T15:20:46.323Z -->
| E34 | 2026-11-06 7:36 PM | release | 35 min | 60 min | 1.71 | for the whole release: I'll merge it and tell you when android-v23 is out. ▢ around 8:10 PM UAE if the checks pass. | <!-- at=2026-11-06T15:36:26.879Z bg=1 -->
| E35 | 2026-11-06 9:33 PM | release | 70 min | 67 min | 0.96 | I'll test the shuffler, review it, merge it, and tell you when android-v24 is out. | <!-- at=2026-11-06T17:33:51.860Z -->
| E36 | 2026-11-07 2:59 AM | kit | 3 h | 14 min | 0.08 | I'll save the failing emulator test's report on a failed release (▢), then build ▢ select as android-v25, test it, merge it and le | <!-- at=2026-11-06T22:59:07.455Z -->
| E37 | 2026-11-07 11:50 AM | release | 75 min | 74 min | 0.74 | I'll check the build, fix seated ▢ touch look, run a review, then merge and release android-v26 and tell you what to try. ▢ be don | <!-- at=2026-11-07T07:50:39.355Z bg=1 more=25 -->
| E39 | 2026-11-07 2:06 PM | release | 75 min | 50 min | 0.67 | android-v27: Phase 1's last checks, release and approval issue (closed late by hand) | <!-- at=2026-11-07T10:06:00.000Z -->
| E40 | 2026-11-10 11:42 AM | release | 60 min | 51 min | 0.85 | I'll fix the two cut-off labels on the folded screen, clear out the stale notes and the dead ▢, then release android-v28 and send you what | <!-- at=2026-11-10T07:42:20.815Z -->
| E41 | 2026-11-10 1:45 PM | kit | 2 h | 1.1 h | 0.55 | I'll build piece 1 (the shared practice copy, then the real game page), test it with ▢, release it as android-v29 and merge. ▢ be | <!-- at=2026-11-10T09:45:45.326Z bg=1 note=worker_down_6h_(2:30-8:40_PM)_not_counted -->
| E42 | 2026-11-10 2:02 PM | release | 1.5 h | 50 min | 0.56 | I'll finish the Android game page tests, check the screenshots, and release android-v29. ▢ the end I'll send the PR link and the main scree | <!-- at=2026-11-10T10:02:08.930Z bg=1 note=worker_down_6h_(2:30-8:40_PM)_not_counted -->
| E43 | 2026-11-10 9:38 PM | kit | 60 min | 62 min | 0.59 | fix the 4 failing tests, review, run the android-check steps, open the PR and merge android-v30. | <!-- at=2026-11-10T17:38:54.234Z more=45 -->
| E44 | 2026-11-12 3:06 PM | release | 20 min | open |  | I'll wait for android-check and Android speed on PR #731, merge it, then send what to try. | <!-- at=2026-11-12T11:06:36.425Z -->
| E45 | 2026-11-12 3:18 PM | build | 50 min | open |  | I'm re-recording the one failed screenshot, merging android-v31, then getting piece 3a to compile and pass tests. First I'm installing the A | <!-- at=2026-11-12T11:18:31.572Z -->
