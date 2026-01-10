# Feature Suggestions for Dating App

**Generated:** 2026-01-10
**Current Phase:** 1.5

---

## Engagement & Discovery

1. **Super Like** - One per day, signals strong interest, recipient notified immediately
2. **Rewind** - Undo last 5 swipes (premium feature), persisted unlike current undo
3. **Boost** - Profile visibility increase for 30 minutes, limited uses per week
4. **Standouts** - Curated grid of 10 exceptional profiles daily (separate from daily pick)
5. **Question Prompts** - Answer 3 from 50+ prompts (e.g., "My perfect Sunday is...") shown on profile
6. **Video Prompts** - 15-second video responses to prompts (stored as URLs)
7. **Voice Notes** - Record 30s voice intro, plays on profile view
8. **Match Expiration** - Matches expire after 7 days of no conversation to encourage engagement
9. **Second Look** - Revisit passed profiles after 30 days

## Matching Intelligence

10. **Advanced Match Filters** - Filter candidates by education, height, distance before swiping (not dealbreakers)
11. **Compatibility Questions** - 20 optional questions (values, lifestyle), used in match quality
12. **Match Predictions** - Show "72% likely to match" prediction before swiping based on ML
13. **Peak Hours** - Notify users when most of their matches are online
14. **Location History** - Track user's frequent locations, match with people in same areas
15. **Event-Based Matching** - "Both going to concerts this month" tag

## Social & Communication

16. **Conversation Starters** - AI-generated icebreakers based on shared interests/bio
17. **Read Receipts** - Show when messages are read (toggleable)
18. **Message Reactions** - React to messages with emoji (❤️, 😂, 👍)
19. **Photo Verification** - Real-time selfie matching profile photo (blue checkmark badge)
20. **Mutual Friends** - Show count/names of Facebook mutual friends (if linked)

## Gamification & Engagement

21. **Daily Login Streak** - Achievement for 7/30/100 consecutive days, rewards like free boost
22. **Leaderboards** - Anonymous rankings: most matches this week, highest match quality avg
23. **Seasonal Events** - Valentine's Day special achievements, Summer Romance badge
24. **Profile Reviews** - Let matches rate your profile after conversations (aggregate score)
25. **Match Milestones** - Celebrate 1 month/6 months/1 year since match with animation

## Safety & Moderation

26. **Incognito Mode** - Only show profile to people you've liked
27. **Block List Import** - Import phone contacts to auto-block
28. **Safety Check-In** - Optional first-date check-in system with emergency contact
29. **AI Content Moderation** - Scan messages for harassment, auto-flag for review
30. **Travel Mode** - Hide profile when traveling, resume when back home

---

## Notes

- Features 1-9: Core engagement loops building on swipe mechanics
- Features 10-15: Matching algorithm enhancements using existing MatchQualityService patterns
- Features 16-20: Communication features (requires message system implementation)
- Features 21-25: Gamification extensions to existing Achievement system
- Features 26-30: Safety enhancements to existing Block/Report infrastructure

**Architecture Fit:**
- Most features integrate cleanly with existing service architecture
- Communication features (16-19) require new MessageService/MessageStorage
- AI features (12, 29) require external ML service integration
- Social features (20) require OAuth integration with external platforms
