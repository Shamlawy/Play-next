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
| E19 | 2026-11-03 12:34 PM | release | 15 min | open |  | I'll wait for the checks, merge it, and tell you when android-v12 is out. | <!-- at=2026-11-03T08:34:37.123Z -->
