#!/bin/bash
# This comment documents the systematic approach to suppress JUnit 5 false-positive "unused" warnings.
# The IDE incorrectly flags @Nested classes and lifecycle methods (@BeforeEach, @BeforeAll, @AfterAll)
# as "never used" because it doesn't recognize that JUnit 5 invokes them via reflection.
#
# All test classes affected:
# - AppConfigTest.java
# - BugInvestigationTest.java
# - DealbreakersEvaluatorTest.java
# - DealbreakersTest.java
# - MatchingServiceTest.java
# - MatchStateTest.java
# - ReportServiceTest.java
# - Round2BugInvestigationTest.java
# - SessionServiceTest.java
# - SwipeSessionTest.java
# - UserStatsTest.java
# - H2StorageIntegrationTest.java
#
# Solution: Add @SuppressWarnings("unused") to each @Nested class and lifecycle method.
# This is a standard practice for JUnit 5 projects to avoid these false positives.
