package datingapp.storage;

import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.MatchPreferences.PacePreferences.CommunicationStyle;
import datingapp.core.profile.MatchPreferences.PacePreferences.DepthPreference;
import datingapp.core.profile.MatchPreferences.PacePreferences.MessagingFrequency;
import datingapp.core.profile.MatchPreferences.PacePreferences.TimeToFirstDate;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.UserStorage;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One-shot developer seed data inserter.
 *
 * <p>Creates 30 pre-defined, stable-UUID users (10 MALE / 10 FEMALE / 10 OTHER) that
 * cover a broad range of age, location, lifestyle, gender preference, and distance
 * edge cases. This makes the app immediately testable on a fresh database without
 * needing to manually register many accounts.
 *
 * <p><strong>Idempotency:</strong> {@link #seed(UserStorage)} does nothing if the
 * sentinel UUID ({@code SEED_SENTINEL_ID}) already exists in storage — safe to call
 * on every startup without duplicating data.
 *
 * <p><strong>Coordinate clusters used:</strong>
 * <ul>
 *   <li>Tel Aviv city centre (~32.07, 34.79) — most users here
 *   <li>North Tel Aviv / Ramat Aviv (~32.11, 34.80)
 *   <li>Herzliya (~32.17, 34.84) — ~10 km north of Tel Aviv
 *   <li>Ramat Gan (~32.08, 34.82) — ~3 km east of Tel Aviv
 *   <li>Petah Tikva (~32.09, 34.89) — ~9 km east, inside 50 km but outside 5 km
 *   <li>Jerusalem (~31.78, 35.22) — ~54 km south-east, out of 50 km default range
 *   <li>Haifa (~32.81, 34.99) — ~90 km north, out of any default range
 * </ul>
 */
public final class DevDataSeeder {

    private static final Logger LOG = LoggerFactory.getLogger(DevDataSeeder.class);

    /**
     * The UUID of the very first seed user. If this record already exists in
     * storage, seeding is skipped entirely so the operation is idempotent.
     */
    // Stable sentinel UUID used to detect whether seed data has already been inserted.
    // Must be a valid RFC-4122 UUID (only hex digits allowed); "seed" contains 's' which is
    // not a hex char and would cause UUID.fromString() to throw at class load.
    private static final UUID SEED_SENTINEL_ID = UUID.fromString("11111111-1111-1111-1111-000000000001");

    private DevDataSeeder() {
        // Static utility class — no instances
    }

    /**
     * Seeds the database with exactly 30 developer users if they are not already
     * present. This method is idempotent: calling it multiple times has no effect
     * after the first successful run.
     *
     * @param userStorage the storage layer to write seed users into
     */
    public static void seed(UserStorage userStorage) {
        // Fast-path: if the sentinel user exists, seeding already ran.
        Optional<User> existing = userStorage.get(SEED_SENTINEL_ID);
        if (existing.isPresent()) {
            LOG.debug("DevDataSeeder: seed data already present, skipping.");
            return;
        }

        LOG.info("DevDataSeeder: inserting 30 seed users for development/testing...");
        buildAllSeedUsers().forEach(userStorage::save);
        LOG.info("DevDataSeeder: seed data inserted successfully.");
    }

    /**
     * Seeds users, and also seeds pre-made matches and a sample conversation so
     * the chat feature works immediately after login without requiring any swipes.
     *
     * <p>Adam Cohen (M1) is matched with both Avital Katz (F1) and Batel Oron (F2).
     * A short sample conversation is seeded between Adam and Avital.
     */
    public static void seed(
            UserStorage userStorage, InteractionStorage interactionStorage, CommunicationStorage communicationStorage) {
        seed(userStorage);
        seedMatchesIfAbsent(interactionStorage, communicationStorage);
    }

    private static void seedMatchesIfAbsent(
            InteractionStorage interactionStorage, CommunicationStorage communicationStorage) {
        UUID adamId = SEED_SENTINEL_ID; // M1 — Adam Cohen
        UUID avitalId = uuid(11); // F1 — Avital Katz
        UUID batelId = uuid(12); // F2 — Batel Oron

        String matchSentinelId = Match.generateId(adamId, avitalId);
        if (interactionStorage.get(matchSentinelId).isPresent()) {
            LOG.debug("DevDataSeeder: seed matches already present, skipping.");
            return;
        }

        LOG.info("DevDataSeeder: inserting seed matches and sample conversation...");

        // Active match: Adam ↔ Avital
        interactionStorage.save(Match.create(adamId, avitalId));

        // Active match: Adam ↔ Batel (no messages — tests the "match exists, no chat yet" path)
        interactionStorage.save(Match.create(adamId, batelId));

        // Seed a short sample conversation between Adam and Avital
        Conversation convo = Conversation.create(adamId, avitalId);
        communicationStorage.saveConversation(convo);

        Message msg1 = Message.create(convo.getId(), avitalId, "Hey Adam! I saw you like hiking too");
        communicationStorage.saveMessage(msg1);

        Message msg2 = Message.create(convo.getId(), adamId, "Haha yeah! We should grab coffee sometime.");
        communicationStorage.saveMessage(msg2);

        Message msg3 = Message.create(convo.getId(), avitalId, "Sounds perfect! When are you free?");
        communicationStorage.saveMessage(msg3);
        communicationStorage.updateConversationLastMessageAt(convo.getId(), msg3.createdAt());

        LOG.info("DevDataSeeder: seed matches and sample conversation inserted.");
    }

    // ════════════════════════════════════════════════════════════════════════
    // Seed user definitions
    // 10 MALE, 10 FEMALE, 10 OTHER — wide variety of preferences, ages,
    // locations (including out-of-range), and gender preferences.
    // ════════════════════════════════════════════════════════════════════════

    @SuppressWarnings("checkstyle:MethodLength")
    private static List<User> buildAllSeedUsers() {
        // ── Interest shorthand aliases ────────────────────────────────────────
        // Alias names are descriptive so seed definitions stay readable.
        final Interest hiking = Interest.HIKING;
        final Interest coffee = Interest.COFFEE;
        final Interest travel = Interest.TRAVEL;
        final Interest cooking = Interest.COOKING;
        final Interest music = Interest.MUSIC;
        final Interest reading = Interest.READING;
        final Interest yoga = Interest.YOGA;
        final Interest gym = Interest.GYM;
        final Interest photography = Interest.PHOTOGRAPHY;
        final Interest concerts = Interest.CONCERTS;
        final Interest movies = Interest.MOVIES;
        final Interest baking = Interest.BAKING;
        final Interest theater = Interest.THEATER;
        final Interest cycling = Interest.CYCLING;
        final Interest running = Interest.RUNNING;
        final Interest swimming = Interest.SWIMMING;
        final Interest dancing = Interest.DANCING;
        final Interest pets = Interest.PETS;
        final Interest dogs = Interest.DOGS;
        final Interest podcasts = Interest.PODCASTS;
        final Interest writing = Interest.WRITING;
        final Interest coding = Interest.CODING;
        final Interest boardGames = Interest.BOARD_GAMES;

        return List.of(
                // ── 10 MALE users ──────────────────────────────────────────────────────

                // M1 — Standard MALE interested in FEMALE. Sentinel / first user.
                //      Matches F1, F2, F3(everyone), F5, F9 based on mutual preferences.
                build(
                        SEED_SENTINEL_ID,
                        "Adam Cohen",
                        LocalDate.of(1994, 3, 15),
                        Gender.MALE,
                        genders(Gender.FEMALE),
                        "Startup founder who loves hiking and good coffee.",
                        32.07,
                        34.79,
                        50,
                        22,
                        35,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.SOCIALLY,
                        Lifestyle.WantsKids.SOMEDAY,
                        Lifestyle.LookingFor.LONG_TERM,
                        Lifestyle.Education.BACHELORS,
                        180,
                        interests(hiking, coffee, travel),
                        pace(
                                MessagingFrequency.OFTEN,
                                TimeToFirstDate.FEW_DAYS,
                                CommunicationStyle.TEXT_ONLY,
                                DepthPreference.DEEP_CHAT)),

                // M2 — MALE interested in FEMALE. Narrow age range (27–33).
                build(
                        uuid(2),
                        "Ben Levi",
                        LocalDate.of(1991, 7, 22),
                        Gender.MALE,
                        genders(Gender.FEMALE),
                        "Software engineer, gym rat, weekend traveler.",
                        32.11,
                        34.80,
                        50,
                        27,
                        33,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.NEVER,
                        Lifestyle.WantsKids.OPEN,
                        Lifestyle.LookingFor.LONG_TERM,
                        Lifestyle.Education.MASTERS,
                        185,
                        interests(gym, travel, cooking),
                        pace(
                                MessagingFrequency.OFTEN,
                                TimeToFirstDate.WEEKS,
                                CommunicationStyle.MIX_OF_EVERYTHING,
                                DepthPreference.DEEP_CHAT)),

                // M3 — MALE interested in FEMALE + OTHER (bi preference).
                build(
                        uuid(3),
                        "Chen Mizrahi",
                        LocalDate.of(1996, 11, 5),
                        Gender.MALE,
                        genders(Gender.FEMALE, Gender.OTHER),
                        "Musician and art lover. Fluent coffee drinker.",
                        32.09,
                        34.82,
                        80,
                        20,
                        30,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.SOCIALLY,
                        Lifestyle.WantsKids.NO,
                        Lifestyle.LookingFor.CASUAL,
                        Lifestyle.Education.BACHELORS,
                        175,
                        interests(music, photography, coffee),
                        pace(
                                MessagingFrequency.OFTEN,
                                TimeToFirstDate.FEW_DAYS,
                                CommunicationStyle.TEXT_ONLY,
                                DepthPreference.SMALL_TALK)),

                // M4 — MALE open to EVERYONE (all genders). Herzliya. Wide radius.
                build(
                        uuid(4),
                        "Daniel Shapiro",
                        LocalDate.of(1989, 4, 30),
                        Gender.MALE,
                        genders(Gender.MALE, Gender.FEMALE, Gender.OTHER),
                        "Chef and food blogger. Open-minded, adventurous.",
                        32.17,
                        34.84,
                        100,
                        25,
                        45,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.SOCIALLY,
                        Lifestyle.WantsKids.OPEN,
                        Lifestyle.LookingFor.LONG_TERM,
                        Lifestyle.Education.BACHELORS,
                        178,
                        interests(cooking, travel, music),
                        pace(
                                MessagingFrequency.OFTEN,
                                TimeToFirstDate.WEEKS,
                                CommunicationStyle.MIX_OF_EVERYTHING,
                                DepthPreference.DEEP_CHAT)),

                // M5 — MALE interested in MALE only (gay). Mutual match with M6.
                build(
                        uuid(5),
                        "Ethan Bar",
                        LocalDate.of(1993, 9, 18),
                        Gender.MALE,
                        genders(Gender.MALE),
                        "Architect. Bookworm. Loves long runs and even longer books.",
                        32.07,
                        34.78,
                        50,
                        22,
                        38,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.SOCIALLY,
                        Lifestyle.WantsKids.NO,
                        Lifestyle.LookingFor.LONG_TERM,
                        Lifestyle.Education.MASTERS,
                        183,
                        interests(reading, running, travel),
                        pace(
                                MessagingFrequency.OFTEN,
                                TimeToFirstDate.FEW_DAYS,
                                CommunicationStyle.MIX_OF_EVERYTHING,
                                DepthPreference.DEEP_CHAT)),

                // M6 — MALE interested in MALE only (gay). Mutual match with M5.
                build(
                        uuid(6),
                        "Finn Rosenberg",
                        LocalDate.of(1995, 2, 14),
                        Gender.MALE,
                        genders(Gender.MALE),
                        "UX designer. Coffee shop hopper. Dog person.",
                        32.08,
                        34.79,
                        50,
                        22,
                        35,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.SOCIALLY,
                        Lifestyle.WantsKids.OPEN,
                        Lifestyle.LookingFor.LONG_TERM,
                        Lifestyle.Education.BACHELORS,
                        177,
                        interests(coffee, dogs, photography),
                        pace(
                                MessagingFrequency.OFTEN,
                                TimeToFirstDate.FEW_DAYS,
                                CommunicationStyle.TEXT_ONLY,
                                DepthPreference.SMALL_TALK)),

                // M7 — MALE interested in FEMALE. Jerusalem (~54 km away).
                //      Distance edge case: filtered by any seeker with maxDistance < 55.
                build(
                        uuid(7),
                        "Gil Avraham",
                        LocalDate.of(1987, 6, 10),
                        Gender.MALE,
                        genders(Gender.FEMALE),
                        "History teacher and amateur archaeologist.",
                        31.78,
                        35.22,
                        100,
                        25,
                        45,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.NEVER,
                        Lifestyle.WantsKids.SOMEDAY,
                        Lifestyle.LookingFor.LONG_TERM,
                        Lifestyle.Education.MASTERS,
                        175,
                        interests(travel, reading, cycling),
                        pace(
                                MessagingFrequency.RARELY,
                                TimeToFirstDate.MONTHS,
                                CommunicationStyle.VOICE_NOTES,
                                DepthPreference.DEEP_CHAT)),

                // M8 — MALE interested in FEMALE. Very tight max distance (5 km).
                //      Tests ultra-narrow distance filter — only sees users within 5 km.
                build(
                        uuid(8),
                        "Harel Dan",
                        LocalDate.of(1998, 1, 25),
                        Gender.MALE,
                        genders(Gender.FEMALE),
                        "Yoga instructor. Plant-based lifestyle advocate.",
                        32.08,
                        34.82,
                        5,
                        20,
                        30,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.NEVER,
                        Lifestyle.WantsKids.NO,
                        Lifestyle.LookingFor.CASUAL,
                        Lifestyle.Education.BACHELORS,
                        172,
                        interests(yoga, cooking, running),
                        pace(
                                MessagingFrequency.OFTEN,
                                TimeToFirstDate.FEW_DAYS,
                                CommunicationStyle.TEXT_ONLY,
                                DepthPreference.SMALL_TALK)),

                // M9 — MALE interested in FEMALE. Wider age range (35–55).
                //      Tests: only sees older FEMALE candidates.
                build(
                        uuid(9),
                        "Ido Weiss",
                        LocalDate.of(1980, 8, 3),
                        Gender.MALE,
                        genders(Gender.FEMALE),
                        "Finance manager, jazz lover, father of two.",
                        32.10,
                        34.81,
                        80,
                        35,
                        55,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.SOCIALLY,
                        Lifestyle.WantsKids.HAS_KIDS,
                        Lifestyle.LookingFor.LONG_TERM,
                        Lifestyle.Education.MASTERS,
                        181,
                        interests(music, travel, cooking),
                        pace(
                                MessagingFrequency.OFTEN,
                                TimeToFirstDate.WEEKS,
                                CommunicationStyle.MIX_OF_EVERYTHING,
                                DepthPreference.DEEP_CHAT)),

                // M10 — MALE open to EVERYONE. Haifa (~90 km). Distance edge case.
                //       Even though he is open to all genders, his 200 km radius is the
                //       binding side; seekers with 50 km max distance won't find him.
                build(
                        uuid(10),
                        "Jake Peretz",
                        LocalDate.of(1992, 12, 7),
                        Gender.MALE,
                        genders(Gender.MALE, Gender.FEMALE, Gender.OTHER),
                        "Marine biologist. Sea lover. Professional scuba diver.",
                        32.81,
                        34.99,
                        200,
                        22,
                        40,
                        Lifestyle.Smoking.SOMETIMES,
                        Lifestyle.Drinking.SOCIALLY,
                        Lifestyle.WantsKids.OPEN,
                        Lifestyle.LookingFor.CASUAL,
                        Lifestyle.Education.MASTERS,
                        182,
                        interests(swimming, travel, running),
                        pace(
                                MessagingFrequency.RARELY,
                                TimeToFirstDate.WEEKS,
                                CommunicationStyle.MIX_OF_EVERYTHING,
                                DepthPreference.SMALL_TALK)),

                // ── 10 FEMALE users ────────────────────────────────────────────────────

                // F1 — FEMALE interested in MALE. Tel Aviv. Matches M1, M4.
                build(
                        uuid(11),
                        "Avital Katz",
                        LocalDate.of(1995, 5, 20),
                        Gender.FEMALE,
                        genders(Gender.MALE),
                        "Pediatric nurse. Loves yoga, hiking and quiet evenings.",
                        32.07,
                        34.79,
                        60,
                        25,
                        38,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.SOCIALLY,
                        Lifestyle.WantsKids.SOMEDAY,
                        Lifestyle.LookingFor.LONG_TERM,
                        Lifestyle.Education.BACHELORS,
                        165,
                        interests(hiking, yoga, cooking),
                        pace(
                                MessagingFrequency.OFTEN,
                                TimeToFirstDate.FEW_DAYS,
                                CommunicationStyle.TEXT_ONLY,
                                DepthPreference.DEEP_CHAT)),

                // F2 — FEMALE interested in MALE. Ramat Aviv. Open age range (28–45).
                build(
                        uuid(12),
                        "Batel Oron",
                        LocalDate.of(1992, 9, 11),
                        Gender.FEMALE,
                        genders(Gender.MALE, Gender.OTHER),
                        "Graphic designer who paints on weekends.",
                        32.11,
                        34.81,
                        50,
                        28,
                        45,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.NEVER,
                        Lifestyle.WantsKids.OPEN,
                        Lifestyle.LookingFor.LONG_TERM,
                        Lifestyle.Education.BACHELORS,
                        168,
                        interests(photography, travel, theater),
                        pace(
                                MessagingFrequency.OFTEN,
                                TimeToFirstDate.WEEKS,
                                CommunicationStyle.MIX_OF_EVERYTHING,
                                DepthPreference.DEEP_CHAT)),

                // F3 — FEMALE open to EVERYONE (all genders). Wide match target.
                //      All gender-preference tests that include FEMALE + OTHER seekers
                //      should find her.
                build(
                        uuid(13),
                        "Chen Nachum",
                        LocalDate.of(1990, 1, 28),
                        Gender.FEMALE,
                        genders(Gender.MALE, Gender.FEMALE, Gender.OTHER),
                        "Psychologist and mindfulness coach.",
                        32.08,
                        34.78,
                        80,
                        24,
                        45,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.SOCIALLY,
                        Lifestyle.WantsKids.OPEN,
                        Lifestyle.LookingFor.LONG_TERM,
                        Lifestyle.Education.MASTERS,
                        162,
                        interests(reading, yoga, music),
                        pace(
                                MessagingFrequency.RARELY,
                                TimeToFirstDate.WEEKS,
                                CommunicationStyle.MIX_OF_EVERYTHING,
                                DepthPreference.DEEP_CHAT)),

                // F4 — FEMALE interested in FEMALE only (lesbian). Mutual match with F6.
                build(
                        uuid(14),
                        "Dana Sagi",
                        LocalDate.of(1994, 7, 4),
                        Gender.FEMALE,
                        genders(Gender.FEMALE),
                        "Environmental lawyer. Weekend surfer.",
                        32.06,
                        34.77,
                        60,
                        24,
                        38,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.SOCIALLY,
                        Lifestyle.WantsKids.NO,
                        Lifestyle.LookingFor.LONG_TERM,
                        Lifestyle.Education.MASTERS,
                        170,
                        interests(swimming, travel, pets),
                        pace(
                                MessagingFrequency.OFTEN,
                                TimeToFirstDate.FEW_DAYS,
                                CommunicationStyle.TEXT_ONLY,
                                DepthPreference.DEEP_CHAT)),

                // F5 — FEMALE interested in MALE. Herzliya. Narrow age range (30–42).
                build(
                        uuid(15),
                        "Ella Raz",
                        LocalDate.of(1988, 3, 17),
                        Gender.FEMALE,
                        genders(Gender.MALE),
                        "Marketing director. Pilates addict. Cat mom.",
                        32.16,
                        34.83,
                        50,
                        30,
                        42,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.SOCIALLY,
                        Lifestyle.WantsKids.SOMEDAY,
                        Lifestyle.LookingFor.LONG_TERM,
                        Lifestyle.Education.MASTERS,
                        164,
                        interests(yoga, cooking, pets),
                        pace(
                                MessagingFrequency.OFTEN,
                                TimeToFirstDate.WEEKS,
                                CommunicationStyle.MIX_OF_EVERYTHING,
                                DepthPreference.DEEP_CHAT)),

                // F6 — FEMALE interested in FEMALE only (lesbian). Mutual match with F4.
                build(
                        uuid(16),
                        "Fay Stern",
                        LocalDate.of(1993, 10, 9),
                        Gender.FEMALE,
                        genders(Gender.FEMALE),
                        "Tech startup co-founder. Board game enthusiast.",
                        32.07,
                        34.80,
                        60,
                        25,
                        40,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.SOCIALLY,
                        Lifestyle.WantsKids.OPEN,
                        Lifestyle.LookingFor.LONG_TERM,
                        Lifestyle.Education.BACHELORS,
                        167,
                        interests(boardGames, coffee, travel),
                        pace(
                                MessagingFrequency.OFTEN,
                                TimeToFirstDate.FEW_DAYS,
                                CommunicationStyle.TEXT_ONLY,
                                DepthPreference.SMALL_TALK)),

                // F7 — FEMALE interested in MALE. Petah Tikva (~9 km east).
                //      Inside 50 km radius but outside the 5 km tight M8 filter.
                build(
                        uuid(17),
                        "Gal Harel",
                        LocalDate.of(1996, 6, 21),
                        Gender.FEMALE,
                        genders(Gender.MALE),
                        "Elementary school teacher. Loves baking and pottery.",
                        32.09,
                        34.89,
                        50,
                        22,
                        35,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.NEVER,
                        Lifestyle.WantsKids.SOMEDAY,
                        Lifestyle.LookingFor.LONG_TERM,
                        Lifestyle.Education.BACHELORS,
                        163,
                        interests(baking, reading, dancing),
                        pace(
                                MessagingFrequency.RARELY,
                                TimeToFirstDate.WEEKS,
                                CommunicationStyle.VOICE_NOTES,
                                DepthPreference.DEEP_CHAT)),

                // F8 — FEMALE (22), interested in MALE. Strict age preference (21–27).
                //      Tests bidirectional age filter: won't show up for 35+ year old seekers.
                build(
                        uuid(18),
                        "Hila Baruch",
                        LocalDate.of(2002, 2, 2),
                        Gender.FEMALE,
                        genders(Gender.MALE),
                        "Film student and aspiring director.",
                        32.07,
                        34.79,
                        50,
                        21,
                        27,
                        Lifestyle.Smoking.SOMETIMES,
                        Lifestyle.Drinking.SOCIALLY,
                        Lifestyle.WantsKids.NO,
                        Lifestyle.LookingFor.CASUAL,
                        Lifestyle.Education.SOME_COLLEGE,
                        160,
                        interests(movies, music, travel),
                        pace(
                                MessagingFrequency.OFTEN,
                                TimeToFirstDate.FEW_DAYS,
                                CommunicationStyle.TEXT_ONLY,
                                DepthPreference.SMALL_TALK)),

                // F9 — FEMALE (45), interested in MALE. Wide age range (35–55).
                //      Matches M9 (Ido Weiss, 46) who also wants 35–55.
                build(
                        uuid(19),
                        "Iris Golan",
                        LocalDate.of(1980, 11, 30),
                        Gender.FEMALE,
                        genders(Gender.MALE),
                        "Architect. Two adult kids. Loves jazz evenings.",
                        32.08,
                        34.80,
                        80,
                        35,
                        55,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.SOCIALLY,
                        Lifestyle.WantsKids.HAS_KIDS,
                        Lifestyle.LookingFor.LONG_TERM,
                        Lifestyle.Education.MASTERS,
                        168,
                        interests(music, travel, reading),
                        pace(
                                MessagingFrequency.OFTEN,
                                TimeToFirstDate.WEEKS,
                                CommunicationStyle.MIX_OF_EVERYTHING,
                                DepthPreference.DEEP_CHAT)),

                // F10 — FEMALE interested in MALE. Jerusalem (~54 km). Out of range.
                //       Distance edge case: won't appear for Tel Aviv seekers with 50 km limit.
                build(
                        uuid(20),
                        "Julia Ben-David",
                        LocalDate.of(1985, 4, 14),
                        Gender.FEMALE,
                        genders(Gender.MALE),
                        "University professor and novelist.",
                        31.77,
                        35.21,
                        100,
                        30,
                        50,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.SOCIALLY,
                        Lifestyle.WantsKids.HAS_KIDS,
                        Lifestyle.LookingFor.LONG_TERM,
                        Lifestyle.Education.PHD,
                        166,
                        interests(reading, travel, writing),
                        pace(
                                MessagingFrequency.RARELY,
                                TimeToFirstDate.MONTHS,
                                CommunicationStyle.VOICE_NOTES,
                                DepthPreference.DEEP_CHAT)),

                // ── 10 OTHER users ─────────────────────────────────────────────────────

                // O1 — OTHER interested in FEMALE + OTHER. Tel Aviv.
                //      Matches F3 (everyone) and O4/O5 (if they like OTHER).
                build(
                        uuid(21),
                        "Alex Tal",
                        LocalDate.of(1997, 8, 15),
                        Gender.OTHER,
                        genders(Gender.FEMALE, Gender.OTHER),
                        "Non-binary barista and zine creator.",
                        32.07,
                        34.79,
                        60,
                        21,
                        35,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.NEVER,
                        Lifestyle.WantsKids.NO,
                        Lifestyle.LookingFor.CASUAL,
                        Lifestyle.Education.BACHELORS,
                        168,
                        interests(photography, coffee, music),
                        pace(
                                MessagingFrequency.OFTEN,
                                TimeToFirstDate.FEW_DAYS,
                                CommunicationStyle.TEXT_ONLY,
                                DepthPreference.SMALL_TALK)),

                // O2 — OTHER open to EVERYONE. Ramat Aviv. Broad match target.
                build(
                        uuid(22),
                        "Blair Nir",
                        LocalDate.of(1991, 3, 22),
                        Gender.OTHER,
                        genders(Gender.MALE, Gender.FEMALE, Gender.OTHER),
                        "Therapist, runner, amateur photographer.",
                        32.12,
                        34.80,
                        80,
                        24,
                        42,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.SOCIALLY,
                        Lifestyle.WantsKids.OPEN,
                        Lifestyle.LookingFor.LONG_TERM,
                        Lifestyle.Education.MASTERS,
                        173,
                        interests(running, yoga, travel),
                        pace(
                                MessagingFrequency.OFTEN,
                                TimeToFirstDate.WEEKS,
                                CommunicationStyle.MIX_OF_EVERYTHING,
                                DepthPreference.DEEP_CHAT)),

                // O3 — OTHER interested in MALE only. Tel Aviv.
                //      Matches M4 (everyone) and M3 (likes FEMALE+OTHER).
                //      M1/M2 do NOT match because they only like FEMALE.
                build(
                        uuid(23),
                        "Casey Dror",
                        LocalDate.of(1994, 6, 5),
                        Gender.OTHER,
                        genders(Gender.MALE),
                        "Sound engineer and music producer.",
                        32.09,
                        34.78,
                        60,
                        22,
                        38,
                        Lifestyle.Smoking.SOMETIMES,
                        Lifestyle.Drinking.SOCIALLY,
                        Lifestyle.WantsKids.NO,
                        Lifestyle.LookingFor.CASUAL,
                        Lifestyle.Education.BACHELORS,
                        170,
                        interests(music, podcasts, concerts),
                        pace(
                                MessagingFrequency.OFTEN,
                                TimeToFirstDate.FEW_DAYS,
                                CommunicationStyle.TEXT_ONLY,
                                DepthPreference.SMALL_TALK)),

                // O4 — OTHER interested in OTHER only. Mutual match with O5.
                build(
                        uuid(24),
                        "Drew Eitan",
                        LocalDate.of(1995, 11, 19),
                        Gender.OTHER,
                        genders(Gender.OTHER),
                        "Fashion designer. Vintage collector. Tea over coffee.",
                        32.07,
                        34.80,
                        50,
                        22,
                        38,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.NEVER,
                        Lifestyle.WantsKids.NO,
                        Lifestyle.LookingFor.LONG_TERM,
                        Lifestyle.Education.BACHELORS,
                        165,
                        interests(dancing, theater, reading),
                        pace(
                                MessagingFrequency.OFTEN,
                                TimeToFirstDate.WEEKS,
                                CommunicationStyle.MIX_OF_EVERYTHING,
                                DepthPreference.DEEP_CHAT)),

                // O5 — OTHER interested in OTHER only. Mutual match with O4.
                build(
                        uuid(25),
                        "Eden Mor",
                        LocalDate.of(1993, 4, 8),
                        Gender.OTHER,
                        genders(Gender.OTHER),
                        "Biotech researcher. Loves bouldering and podcasts.",
                        32.08,
                        34.81,
                        50,
                        24,
                        40,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.SOCIALLY,
                        Lifestyle.WantsKids.OPEN,
                        Lifestyle.LookingFor.LONG_TERM,
                        Lifestyle.Education.MASTERS,
                        172,
                        interests(running, reading, podcasts),
                        pace(
                                MessagingFrequency.RARELY,
                                TimeToFirstDate.WEEKS,
                                CommunicationStyle.MIX_OF_EVERYTHING,
                                DepthPreference.DEEP_CHAT)),

                // O6 — OTHER open to EVERYONE. Herzliya. Wide radius (100 km).
                build(
                        uuid(26),
                        "Frankie Lior",
                        LocalDate.of(1998, 9, 1),
                        Gender.OTHER,
                        genders(Gender.MALE, Gender.FEMALE, Gender.OTHER),
                        "DJ and event organiser. Night owl.",
                        32.17,
                        34.83,
                        100,
                        20,
                        35,
                        Lifestyle.Smoking.SOMETIMES,
                        Lifestyle.Drinking.SOCIALLY,
                        Lifestyle.WantsKids.NO,
                        Lifestyle.LookingFor.CASUAL,
                        Lifestyle.Education.SOME_COLLEGE,
                        167,
                        interests(music, dancing, concerts),
                        pace(
                                MessagingFrequency.OFTEN,
                                TimeToFirstDate.FEW_DAYS,
                                CommunicationStyle.TEXT_ONLY,
                                DepthPreference.SMALL_TALK)),

                // O7 — OTHER interested in FEMALE only. Tel Aviv.
                //      Matches F3 (everyone). F4 does NOT match (she only likes FEMALE,
                //      not OTHER).
                build(
                        uuid(27),
                        "Gael Ofer",
                        LocalDate.of(1990, 7, 13),
                        Gender.OTHER,
                        genders(Gender.FEMALE),
                        "Landscape architect who paints watercolors.",
                        32.10,
                        34.82,
                        70,
                        25,
                        42,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.NEVER,
                        Lifestyle.WantsKids.OPEN,
                        Lifestyle.LookingFor.LONG_TERM,
                        Lifestyle.Education.MASTERS,
                        175,
                        interests(cycling, travel, photography),
                        pace(
                                MessagingFrequency.RARELY,
                                TimeToFirstDate.WEEKS,
                                CommunicationStyle.VOICE_NOTES,
                                DepthPreference.DEEP_CHAT)),

                // O8 — OTHER interested in MALE + OTHER. Ramat Gan.
                build(
                        uuid(28),
                        "Harper Ziv",
                        LocalDate.of(1992, 2, 27),
                        Gender.OTHER,
                        genders(Gender.MALE, Gender.OTHER),
                        "Stand-up comedian. Podcast host. Avid reader.",
                        32.09,
                        34.83,
                        60,
                        24,
                        40,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.SOCIALLY,
                        Lifestyle.WantsKids.NO,
                        Lifestyle.LookingFor.CASUAL,
                        Lifestyle.Education.BACHELORS,
                        171,
                        interests(reading, music, podcasts),
                        pace(
                                MessagingFrequency.OFTEN,
                                TimeToFirstDate.FEW_DAYS,
                                CommunicationStyle.TEXT_ONLY,
                                DepthPreference.SMALL_TALK)),

                // O9 — OTHER (40), open to EVERYONE. Wide age (28–55). Older "everyone" profile.
                build(
                        uuid(29),
                        "Indie Sasson",
                        LocalDate.of(1985, 5, 25),
                        Gender.OTHER,
                        genders(Gender.MALE, Gender.FEMALE, Gender.OTHER),
                        "Yoga retreat organiser and meditation teacher.",
                        32.08,
                        34.79,
                        100,
                        28,
                        55,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.NEVER,
                        Lifestyle.WantsKids.HAS_KIDS,
                        Lifestyle.LookingFor.LONG_TERM,
                        Lifestyle.Education.BACHELORS,
                        164,
                        interests(yoga, travel, dogs),
                        pace(
                                MessagingFrequency.RARELY,
                                TimeToFirstDate.WEEKS,
                                CommunicationStyle.VOICE_NOTES,
                                DepthPreference.DEEP_CHAT)),

                // O10 — OTHER interested in MALE only. Jerusalem. Out of range.
                //       Distance edge case for OTHER-gender user.
                build(
                        uuid(30),
                        "Jordan Naor",
                        LocalDate.of(1996, 1, 9),
                        Gender.OTHER,
                        genders(Gender.MALE),
                        "Data scientist and amateur astronomer.",
                        31.79,
                        35.20,
                        120,
                        22,
                        38,
                        Lifestyle.Smoking.NEVER,
                        Lifestyle.Drinking.SOCIALLY,
                        Lifestyle.WantsKids.OPEN,
                        Lifestyle.LookingFor.LONG_TERM,
                        Lifestyle.Education.MASTERS,
                        170,
                        interests(travel, coding, reading),
                        pace(
                                MessagingFrequency.OFTEN,
                                TimeToFirstDate.WEEKS,
                                CommunicationStyle.MIX_OF_EVERYTHING,
                                DepthPreference.DEEP_CHAT)));
    }

    // ════════════════════════════════════════════════════════════════════════
    // Builder helpers — extracted to keep seed definitions concise
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Constructs a fully-formed, ACTIVE seed user. Uses two-arg setters where the
     * system limit is required. Throws if the profile is still incomplete when
     * {@code activate()} is called — surfaces misconfigured seed entries early.
     */
    @SuppressWarnings({"checkstyle:ParameterNumber", "PMD.ExcessiveParameterList"})
    private static User build(
            UUID id,
            String name,
            LocalDate birthDate,
            Gender gender,
            Set<Gender> interestedIn,
            String bio,
            double lat,
            double lon,
            int maxDistanceKm,
            int minAge,
            int maxAge,
            Lifestyle.Smoking smoking,
            Lifestyle.Drinking drinking,
            Lifestyle.WantsKids wantsKids,
            Lifestyle.LookingFor lookingFor,
            Lifestyle.Education education,
            int heightCm,
            Set<Interest> interests,
            PacePreferences pace) {

        User user = new User(id, name);
        user.setBio(bio);
        user.setBirthDate(birthDate);
        user.setGender(gender);
        user.setInterestedIn(interestedIn);
        user.setLocation(lat, lon);

        // Clamp to system-enforced upper limit (500 km)
        user.setMaxDistanceKm(Math.min(maxDistanceKm, 500), 500);

        // Use two-arg variant with system bounds (18–120)
        user.setAgeRange(Math.max(minAge, 18), Math.min(maxAge, 120), 18, 120);

        user.setSmoking(smoking);
        user.setDrinking(drinking);
        user.setWantsKids(wantsKids);
        user.setLookingFor(lookingFor);
        user.setEducation(education);
        user.setHeightCm(heightCm);
        user.setInterests(interests);
        user.setPacePreferences(pace);

        // Deterministic avatar so the profile passes isComplete() without a real photo.
        user.addPhotoUrl("https://api.dicebear.com/9.x/avataaars/png?seed=" + id);

        // Activate immediately — will throw if any required field is still absent,
        // making bad seed entries fail loudly at startup rather than silently.
        user.activate();
        return user;
    }

    /** Builds a {@link Set} of genders from varargs. */
    @SafeVarargs
    private static Set<Gender> genders(Gender... values) {
        return EnumSet.copyOf(List.of(values));
    }

    /** Builds a {@link Set} of interests from varargs. */
    @SafeVarargs
    private static Set<Interest> interests(Interest... values) {
        return EnumSet.copyOf(List.of(values));
    }

    /** Constructs a {@link PacePreferences} record from the four required fields. */
    private static PacePreferences pace(
            MessagingFrequency freq, TimeToFirstDate time, CommunicationStyle style, DepthPreference depth) {
        return new PacePreferences(freq, time, style, depth);
    }

    /**
     * Generates a stable, deterministic UUID for seed user index {@code n} (1–30).
     * Format: {@code 11111111-0000-0000-0000-0000000000NN} — all segments are valid hex.
     * The literal word "seed" is not valid hex and would cause UUID.fromString to throw
     * a NumberFormatException at startup.
     */
    private static UUID uuid(int n) {
        return UUID.fromString(String.format("11111111-0000-0000-0000-%012d", n));
    }
}
