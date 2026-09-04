> **⚠ SUPERSEDED — historical draft, do not build from this.**
>
> This is the original v1.0 product direction, kept for its reasoning trail. It describes a
> four-stage spine (`WATCH → TRY → CHALLENGE → MASTER`) that no longer exists: MASTER was
> removed by ADR-017, and CHALLENGE was deferred to V2 by ADR-031.
>
> **The live specification is [`PRODUCT_SPEC.md`](../PRODUCT_SPEC.md).**
> The MVP spine is `WATCH → TRY`; see [`v2-challenge.md`](v2-challenge.md).

---

Interactive DSA Learning App — Product & UX Specification

Version: 1.0
Status: Product direction locked
Core principle: Don't read algorithms. Run them.

1. Product Vision

The app teaches algorithms through interactive experiences rather than passive explanations.

The core learning loop is:

WATCH → TRY → CHALLENGE → MASTER

The user first watches the algorithm being demonstrated through carefully timed animation.

Then the user takes control and performs the algorithm with guidance.

Finally, the user receives a new problem and must independently apply the concept.

The objective is not simply to teach users what an algorithm is.

The objective is to make the user understand:

“I know what decision the algorithm makes, and I can make that decision myself.”

2. Core Product Principles
2.1 Animation is the teaching

Animations must communicate algorithmic concepts.

Decorative animation should not be used inside learning experiences.

Motion is information.

2.2 Experience before theory

Do not begin lessons with long explanations.

The preferred order is:

Experience → Explanation → Application

The user should first see the algorithm work, then receive a concise mental model.

2.3 New data in Challenge

The Challenge must use different data from Watch/ Try.

The user must not be able to succeed by memorizing the previous sequence.

The challenge should test whether the user understood the algorithm.

2.4 Wrong answers should teach

Where a wrong decision creates a meaningful consequence, show that consequence.

Do not simply display:

❌ Wrong

Instead:

Decision → consequence → explanation → recovery

However, this should only be used where the consequence itself teaches something.

2.5 No artificial difficulty

The app should challenge the user's understanding, not their patience.

If an interaction has become repetitive and no longer teaches anything, the app should shorten or change the interaction.

3. Target Experience

The user should feel:

“The app showed me how to think.”

rather than:

“The app showed me an animation.”

The desired emotional progression is:

Curiosity → Understanding → Control → Confidence → Mastery

4. MVP Scope
MVP learning content
Binary Search
The Race — Linear Search vs Binary Search
Two Pointers
Bubble Sort
Selection Sort
Insertion Sort
Sliding Window
Stack or Queue
One additional algorithm/content slot may be added only if it provides clear value.
Important

Stack and Queue are treated as a single capstone concept:

“Stack or Queue?”

The lesson teaches when a problem requires one structure versus the other.

This avoids having two weak lessons based purely on push/pop or enqueue/dequeue operations.

5. Information Architecture

The app has three primary tabs:

Home

Answers:

“What should I do right now?”

Journey

Answers:

“What can I learn next?”

Progress

Answers:

“How am I improving?”

Settings lives behind the Settings icon in Progress.

There is no:

Social tab
Shop
Profile tab
Search tab
separate Code tab

Learning experiences open full-screen with bottom navigation hidden.

6. Navigation

Primary flow:

Home
  ↓
Primary Action
  ↓
Watch
  ↓
Try
  ↓
Challenge
  ↓
Result
  ↓
Next Algorithm

Other flows:

Home
  ↓
Daily Challenge
  ↓
Daily Result
Journey
  ↓
Algorithm
  ↓
Watch / Try / Challenge / Code
Progress
  ↓
Mastery / Streak / Personal Bests / Achievements
7. Resume Behaviour

Leaving an active lesson must never lose progress.

Persist:

algorithm
phase
step index
current state necessary to resume

Example:

Continue Bubble Sort · Try · Step 7

The user should return to exactly where they left.

8. Home Screen

Home has one primary action.

The primary card is state-driven.

Possible states, in priority order:

1. First-time user

Start here — Binary Search · 3 min

2. Lesson in progress

Continue — Bubble Sort · Try · 72%

3. Daily Challenge available

Today's Challenge · 3 rounds

4. Rusty algorithm

Refresh — Binary Search · 45 sec

5. Nothing pending

Next up — Selection Sort · 4 min

There must never be two competing primary CTAs.

Home content

Above the fold:

Streak
Primary action
Daily Challenge

Below:

Current journey section
Weekly activity
Progress

Banner ads may appear at the bottom of Home but must not push or shift the primary action.

9. Journey

Journey is a chapter-based syllabus, not a Duolingo-style winding path.

Example:

SEARCHING

Binary Search       🏆
The Race            🏆
Two Pointers        🎮


SORTING

Bubble Sort         ○
Selection Sort      🔒
Insertion Sort      🔒


WINDOWS

Sliding Window      🔒


STRUCTURES

Stack or Queue      🔒

Each algorithm has a visible state.

States

🔒 Locked

Prerequisite not reached.

👀 Seen

Watch completed.

🎮 Driven

Try completed.

🏆 Mastered

Challenge passed with at least 2 stars.

🌫 Rusty

Mastered but not practiced for 30 days.

Rusty never removes earned mastery.

10. Algorithm Hub

Opening an algorithm should show a learning spine, not four equally weighted buttons.

Example:

Bubble Sort

Sort by repeatedly swapping neighbours.

O(n²) · O(1) · Stable


✓ WATCH
● TRY
○ CHALLENGE
○ CODE

The current phase is visually emphasized.

After Watch, show a concise:

Remember
Compare neighbouring numbers.
If left > right, swap.
Continue through the list.
Repeat until no swaps are needed.
Skip option

A user who already knows an algorithm can:

Skip to Challenge

If they pass the Challenge cold, Watch and Try are marked complete.

This prevents experienced users from being forced through content they already know.

11. WATCH

Watch is a guided demonstration.

The user initially does nothing.

The animation teaches the algorithm.

Watch rhythm

The animation should deliberately pause before important decisions.

Generic rhythm:

Highlight relevant elements.
Hold.
Present the question.
Give the learner a short anticipation pause.
Execute the decision.
Hold.
Explain what happened.
Move to next state.

The goal is to let the user predict what will happen before seeing it.

Watch checkpoint

At least one checkpoint should interrupt passive watching.

Example:

Which half survives?

The user answers.

There is no punishment.

This prepares the transition from:

watching → doing

12. Insight Frame

Every algorithm ends with one strong visual insight.

Examples:

Binary Search

One comparison. Half the array. Gone.

Bubble Sort

The largest number bubbled to the end.

Selection Sort

A whole pass. One swap.

Insertion Sort

The left side is always already sorted.

Two Pointers

Both ends move inward. We never go back.

Sliding Window

The window never re-reads what it already counted.

Stack / Queue

Last in, first out. First in, first out.

13. TRY

Try is guided execution.

Nothing in Try is scored.

The purpose is to build confidence before Challenge.

Flow
Brief replay of the demonstrated mechanic.
Visual handoff of control to the user.
User makes decisions.
App executes the decision.
App explains why.
Continue.

Decision controls must have equal visual weight.

The correct answer must never be visually hinted.

14. Bubble Sort Interaction

This is an important correction.

The user should not choose which pair to compare.

In Bubble Sort, the next adjacent pair is determined by the algorithm.

Instead:

“7 and 8 — swap or keep?”

User chooses:

SWAP

or

KEEP

The algorithm then advances automatically.

This teaches the actual algorithmic decision.

15. Try Mercy Exit

After four consecutive correct decisions:

You've got it — finish this for me →

The app can automatically complete the remaining mechanical steps.

This prevents long sorting exercises from turning into repetitive tapping.

16. Wrong Answer Behaviour
First wrong answer

Do not advance.

Provide a small contextual prompt.

Example:

Look again — is 7 bigger than 3?

No penalty.

Second wrong answer

Where a meaningful consequence exists:

Execute the wrong choice and show the consequence.

Otherwise:

Reveal and explain the correct decision.

Third wrong answer

Automatically solve the current step with explanation.

Never dead-end the learner.

Idle

After inactivity, provide increasingly explicit inline help.

Never use disruptive hint popups.

17. Meaningful Consequences

Wrong-choice consequences should be used selectively.

Binary Search

Wrong half is eliminated.

The user sees that the target has become unreachable.

Two Pointers

Wrong pointer movement can skip the valid solution.

Sliding Window

Incorrect shrinking can lose the best window.

Stack / Queue

Wrong structure produces visibly incorrect output order.

Bubble / Selection / Insertion

Do not manufacture dramatic consequences.

Simply correct the decision and explain it.

18. CHALLENGE

Challenge introduces:

new data
no instructional narration
no guided decision hints
live metrics
independent problem solving

The user should immediately feel:

“Now I'm driving.”

19. Challenge Data Rules

Challenge data must:

be newly generated
have similar difficulty to the lesson
not share the exact data pattern from Watch
differ in solution path from the demonstration

Example:

If Watch starts with a swap, Challenge should preferably start with a keep.

This prevents pattern memorization.

Challenge seeds must be deterministic per attempt.

20. Challenge Formats

There are two formats.

Format A — Operate

Used for:

Binary Search
The Race
Two Pointers
Sliding Window
Stack / Queue

The user performs the algorithm directly.

Format B — Pass Prediction

Used for:

Bubble Sort
Selection Sort
Insertion Sort

Instead of requiring the user to perform every mechanical operation:

“What will this look like after one full pass?”

The user manipulates the predicted result.

For Bubble Sort:

Tap two tiles to swap.

Then:

CHECK

The app animates the actual pass against the user's prediction.

This tests understanding rather than repetitive tapping.

21. Challenge Rules

There is no hard fail state.

A mistake does not destroy the session.

Wrong decisions are recorded and the algorithm continues.

No countdown timer in MVP.

Time is recorded silently and can be used for personal bests.

22. Hints

Every Challenge includes:

One free hint

A second hint can be earned through an optional rewarded ad.

After two failed attempts:

Full walkthrough

is automatically available for free.

Never put the solution/walkthrough behind an ad.

Core learning must never be held hostage.

23. RESULT

Result is not just a scoreboard.

It should provide:

Final result
Mastery
Performance metrics
Personal coaching insight
Next action

Example:

🏆 Bubble Sort Mastered

10 comparisons · 4 swaps · 100% accuracy

You made every decision correctly.

Primary:

NEXT: Selection Sort →

Secondary:

See the code
Try again

The Next action must always be instant.

24. Scoring

Do not use one universal efficiency formula.

Different algorithm families measure different learner-controlled behaviour.

Efficiency algorithms
Linear Search
Binary Search
Two Pointers
Sliding Window

Measure:

comparisons
wrong decisions
efficiency
Accuracy algorithms
Bubble Sort
Selection Sort
Insertion Sort

Measure:

wrong decisions
hints
correct execution

Do not reward users for a lower number of swaps when the number of swaps is determined by the input.

Scenario algorithms
Stack / Queue

Measure:

correct output
operation efficiency
25. Stars
⭐⭐⭐

Excellent / optimal performance.

⭐⭐

Successful understanding.

⭐

Completed with significant mistakes.

Mastery

Mastery is awarded at:

★★ or better

Three stars should give an:

Optimal

badge rather than being required for mastery.

26. Mastery

Mastery states:

Locked
  ↓
Seen
  ↓
Driven
  ↓
Mastered
  ↓
Rusty

Rusty does not erase mastery.

It simply means:

This concept deserves a refresh.

Rusty begins after approximately 30 days without practice.

27. DAILY CHALLENGE

Daily Challenge is not a mini lesson.

It is a recall activity.

It becomes available after the user reaches:

3 algorithms at Driven

Before that:

Unlock Daily Challenge
1 / 3 algorithms

Daily format

Three short rounds.

Each round tests one decisive algorithmic decision.

Example:

Binary Search for 73.

Mid = 45.

Which half survives?

User taps.

Next round.

The entire experience should be approximately one minute.

28. Daily Challenge Scoring

Daily Challenge has:

daily score
personal best

It does not award normal lesson stars.

A failed round does not block the streak.

Completing at least one round maintains the streak.

Completing all three gives the full daily result.

29. Streak

Streak is a motivation mechanism, not a punishment mechanism.

One completed Daily Challenge round is sufficient to maintain the streak.

One earned streak freeze can be awarded periodically.

An additional freeze may be available through rewarded advertising.

30. Progress

Progress shows:

Mastery

Example:

8 / 15 Mastered

Mastery Map

All algorithms and their states.

Streak Calendar

Daily activity.

Personal Bests
fastest time
fewest comparisons
best stars
best performance
Achievements

Skill-based achievements only.

Examples:

Found it in 3 comparisons.

Full sort with zero wrong decisions.

Seven-day streak.

No mystery achievements.

31. CODE REVEAL

Code is a reward/reference after learning, not the primary teaching method.

It becomes available after a passed Challenge.

The transition should visually connect:

“What you just did” → “what that looks like in code.”

Example:

User just made:

Swap or Keep

Code reveal highlights:

if (a[j] > a[j + 1])

with:

This is the decision you were making.

MVP Code

Must have:

Java
Python
syntax-highlighted code
static decision-line annotation
copy code

Full synchronized visualizer/code playback can be added later.

Code is always free.

32. Retention

The app must provide reasons to return after lessons are completed.

Tomorrow

Daily Challenge.

This week

Journey progression and streak.

Weeks 2–4

Personal best improvement.

Around 30 days

Rusty algorithm refresh.

Future

New algorithm/content releases.

The retention loop should remain useful rather than relying purely on gamification.

33. Monetization

The app is:

Free to learn.

No paid algorithms.

No premium lessons.

No subscription in MVP.

Monetization uses AdMob.

34. Ad Rules
Absolute rule

Ads fire on exit paths, never forward paths.

No ads:

inside Watch
inside Try
inside Challenge
inside Daily Challenge
inside Code Reveal
on Next
on Continue
on Try Again
on first session
Banner

Allowed on:

Home
Journey
Progress
Settings

Banner must remain outside the scrolling content so it does not shift the UI.

Interstitial

Potential locations:

Daily Result → Home
Lesson Result → Home when leaving
Algorithm Hub → Journey when leaving

Never when moving forward.

Use a global frequency cap.

Rewarded ads

Rewarded ads provide optional convenience or bonus content.

Possible rewards:

second hint
streak freeze
immediate retry
extra practice pack
bonus daily rounds
temporary ad-free period

Never gate:

knowledge
solution walkthrough
algorithm
mastery
code
progress
35. First Session

The first session must demonstrate the product's uniqueness immediately.

No:

login
onboarding carousel
unnecessary permissions
first-session ad

Start with an interactive algorithm experience.

Target first-session flow
Find 73
   ↓
Binary Search demonstration
   ↓
WOW moment:
"One comparison. Half the array. Gone."
   ↓
User Try
   ↓
Binary Search Driven
   ↓
Challenge
   ↓
Mastery
   ↓
The Race
   ↓
Linear vs Binary insight

The first minute should communicate:

This app doesn't just show algorithms. It makes me use them.

36. Visual Design Direction

The app should feel:

Technical + premium + focused + slightly playful

Not childish.

Reference feeling:

A precision instrument you operate.

Dark theme is the MVP default.

Brand language

Primary brand colour:

Signal Violet

Interface elements use the brand colour.

Algorithm data uses a separate semantic colour system.

This separation must be maintained.

Semantic states
Amber — currently being examined
Cyan — pointer / left boundary
Coral — second pointer / right boundary
Green — correct/finalized/found
Red — consequence of wrong decision
Dimmed — eliminated

The target itself should not depend solely on colour.

37. Typography

Use a technical but approachable type system.

Recommended:

Display: Archivo
Interface: IBM Plex Sans
Data/code: JetBrains Mono

Numbers should use tabular figures so animated arrays do not visually jitter.

38. Layout

Base spacing:

4dp grid

Primary rhythm:

8dp

Screen margin:

20dp

Cards:

16dp radius

Buttons:

14dp radius

Primary CTA:

56dp

Decision controls:

minimum 64dp

Exactly one primary CTA should exist on a normal screen.

39. Animation Principles
Motion is information.

Only animate something if the animation communicates an algorithmic fact.

Important transitions should have a readable pause.

Never animate two important concepts simultaneously.

Examples:

Compare → pause → decision → swap → pause → explanation

Respect Android reduced-motion settings.

40. Accessibility

MVP must support:

readable text
sufficient touch targets
screen/font scaling where practical
semantic labels
reduced motion
colour-independent meaning

Algorithm meaning must not depend only on colour.

41. Offline-first

Core learning must work without an internet connection.

The MVP does not require:

login
cloud account
backend
server-generated challenges

Local persistence stores:

progress
mastery
streak
challenge history
personal bests
current lesson position

Internet-dependent services are limited to things such as:

advertising
analytics
crash reporting
42. Analytics

Track learning behaviour, not just app opens.

Important events:

app_open
lesson_started
watch_completed
try_started
try_completed
challenge_started
challenge_completed
challenge_failed
hint_used
walkthrough_opened
algorithm_driven
algorithm_mastered
daily_started
daily_completed
streak_continued
rewarded_ad_completed

The most important funnel is:

Install
 ↓
First interaction
 ↓
Watch
 ↓
Try
 ↓
Challenge
 ↓
Mastery
 ↓
Day 2 return
 ↓
Day 7 return
43. MVP Explicitly Does NOT Include

Do not build:

leaderboards
social/friends
coins
gems
energy/lives
user-authored graphs
in-app code editor
video courses
subscription
premium algorithms
standalone Big-O playground
complex graph renderer
tree renderer
dynamic programming system
multiple accounts/login in MVP

These can be evaluated after MVP validation.

44. MVP Success Criteria

The MVP should not be judged primarily by:

number of algorithms.

It should be judged by whether users complete the learning loop.

The most important product questions are:

Can a new user understand an algorithm through Watch?
Can they successfully perform it during Try?
Can they solve a NEW problem during Challenge?
Do they return for the Daily Challenge?
Do they feel the app is different from a normal algorithm visualizer?

The core success signal is:

Watch → Try → Challenge completion

followed by:

Day-2 retention.

45. Product Philosophy — Final

The app should never feel like:

“Here is an algorithm. Read this.”

It should feel like:

“Watch me do it.”

Then:

“Your turn.”

Then:

“Here's a new problem. Do it yourself.”

And finally:

“Now you actually understand it.”

LOCKED CORE

WATCH → TRY → CHALLENGE → MASTER

Animation teaches. Interaction reinforces. New problems test understanding.

This is the foundation of the product.