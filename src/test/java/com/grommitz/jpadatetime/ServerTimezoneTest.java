package com.grommitz.jpadatetime;

import com.wix.mysql.EmbeddedMysql;
import com.wix.mysql.ScriptResolver;
import com.wix.mysql.config.MysqldConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static com.wix.mysql.EmbeddedMysql.anEmbeddedMysql;
import static com.wix.mysql.config.Charset.UTF8;
import static com.wix.mysql.config.MysqldConfig.aMysqldConfig;
import static com.wix.mysql.distribution.Version.v8_0_17;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ServerTimezoneTest {

	private static final Logger logger = LoggerFactory.getLogger(ServerTimezoneTest.class.getName());
	private TimeZone tz;
	private static EmbeddedMysql mysqld;
	public static final String JDBC_URL = "jdbc:mysql://localhost:4406/testdb";
	private String jdbcUrl = JDBC_URL;

	@BeforeAll
	static void init() {
		MysqldConfig config = aMysqldConfig(v8_0_17)
				.withCharset(UTF8)
				.withPort(4406)
				.withUser("testuser", "")
				.withTimeZone("Europe/London")
				.withTimeout(2, TimeUnit.MINUTES)
				.build();

		mysqld = anEmbeddedMysql(config)
				.addSchema("testdb", ScriptResolver.classPathScript("setup.sql"))
				.start();
	}

	@AfterAll
	static void finish() {
		mysqld.stop();
	}

	@BeforeEach
	void startDB() {
		tz = TimeZone.getDefault();
	}

	@AfterEach
	void tearDown() {
		TimeZone.setDefault(tz);
	}

	/*
	 * In this test we keep the server in the default timezone but we move the system timezone around.
	 *
	 * Without using serverTimezone param, the driver is able to convert from the MySQL server's timezone
	 * to the system timezone automatically.
	 *
	 * NOTE - This test assumes that you are running it in Europe/London timezone.
	 * If you are not, it won't work unless you change the expected times. If we explicitly set the server's timezone
	 * that defeats the purpose of the test!
	 */
	@ParameterizedTest
	@CsvSource({"US/Eastern, 08:00", "Asia/Shanghai, 21:00", "Europe/London, 13:00"})
	@DisplayName("Converts to system time automatically")
	void convertTimeAutomatically(String timezone, String expectedTime) {
		assumeTrue(TimeZone.getDefault().getID().equals("Europe/London"));

		setSystemTimezone(timezone);
		EntityManager em = connect();

		LocalTime time = em.find(DbThing.class, 1L).getTime().toLocalTime(); // 13:00 in the server's local time

		logger.info("MySQL timezone              : Europe/London");
		logger.info("JVM timezone                : {}", TimeZone.getDefault().getID());
		logger.info("Time stored in the database : 13:00");
		logger.info("Time at the JVM's timezone  : {}", time);

		assertThat("Time in " + TimeZone.getDefault().getID() + " for 1pm in Europe/London should be " + expectedTime,
				time.toString(), is(expectedTime));
	}

	/*
	 * Test that whatever the serverTimezone, a round trip to the database preserves the
	 * correct time.
	 */
	@ParameterizedTest
	@CsvSource({"Europe/London", "US/Eastern", "Asia/Shanghai", "Australia/Canberra"})
	@DisplayName("Times are converted to the MySQL server's timezone on write")
	void writeTest(String mysqlServerTimezone) {

		setMySQLTimezone(mysqlServerTimezone);
		EntityManager em = connect();

		final LocalDate date = LocalDate.of(2020,8,1);
		final LocalTime timeInserted = LocalTime.of(13, 0);
		long id = givenResultAt(em, LocalDateTime.of(date, timeInserted));

		LocalTime timeRetrieved = em.find(DbThing.class, id).getTime().toLocalTime();

		assertThat(timeRetrieved, is(timeInserted));
	}

	/*
	 * In this test we keep the system timezone constant but we move the MySQL server around by setting
	 * serverTimezone=...
	 */
	@ParameterizedTest
	@CsvSource({"Europe/London, 14:00", "US/Eastern, 19:00", "Europe/Paris, 13:00"})
	@DisplayName("Local DB times to be converted to the system's timezone")
	void readConvertsTimes(String mysqlServerTimezone, String eta) {

		setSystemTimezone("Europe/Paris");
		setMySQLTimezone(mysqlServerTimezone);
		EntityManager em = connect();

		// this was inserted in the setup.sql script
		DbThing r = em.find(DbThing.class, 1L);
		String timePart = r.getTime().toLocalTime().toString();

		logger.info("MySQL timezone              : {}", mysqlServerTimezone);
		logger.info("JVM timezone                : {}", TimeZone.getDefault().getID());
		logger.info("Time stored in the database : 13:00");
		logger.info("Time at the JVM's timezone  : {}", r.getTime().toLocalTime());

		assertThat("Time in " + TimeZone.getDefault() + " for 1pm in " + mysqlServerTimezone + " should be " + eta,
				timePart, is(eta));
	}

	@Test
	@DisplayName("Using the serverTimezone changes the meaning of times stored before it was added")
	void usingServerTimezoneChangesSemantics() {
		setSystemTimezone("US/Eastern");

		EntityManager em = connect();
		long id = givenResultAt1PMInTheSystemTimezone(em);

		LocalTime time0 = em.find(DbThing.class, id).getTime().toLocalTime();
		assertThat(time0, is(LocalTime.of(13, 0)));

		setMySQLTimezone("US/Eastern");
		em = connect();
		LocalTime time1 = em.find(DbThing.class, id).getTime().toLocalTime(); // same result, but the time is now different

		assertThat(time1, is(time0.plusHours(5)));
		System.err.println("Oops - adding serverTimezone has pushed the time forward 5h!");
	}

	/*
	 * MySQL seems to allow us to insert this "bad time" which is in the non-existant hour when the clocks go forward.
	 * MariaDB does not allow it to be inserted.
	 */
	@Test
	void badTimeInSpringJumpForward() {
//		setSystemTimezone("Europe/London");
//		setMySQLTimezone("Europe/London");

		EntityManager em = connect();
		long id = givenResultAt(em, LocalDateTime.of(2018, 3, 25,  1, 31, 4));
		LocalTime time0 = em.find(DbThing.class, id).getTime().toLocalTime();
		System.out.println(time0);

		setSystemTimezone("US/Eastern");
		em = connect();
		LocalTime time1 = em.find(DbThing.class, id).getTime().toLocalTime();
		System.out.println(time1);
	}

	/*
		Test the problem that UPDATE queries cause the timestamp to move.
		Conclusion - can't replicate it. Hibernate & the JDBC driver perform
		as expected.
 	*/
	@Test
	void updateTest() {
		setMySQLTimezone("Europe/London");
		setSystemTimezone("US/Eastern");

		// fetch the entry for 1.30am local time on 22/3. it should come out as 9.30pm US time the day before.
		EntityManager em = connect();
		DbThing dbThing = em.find(DbThing.class, 4L);

		{
			// sanity check
			assertThat(dbThing.getTime().toString(), is("2018-03-21T21:30"));
		}

		// now update a different field in the entity, leaving the time field untouched
		dbThing.setUrl("http://update.com");
		em.getTransaction().begin();
		em.merge(dbThing);
		em.getTransaction().commit();

		{
			// refetch, check the time didn't change
			final LocalTime time1 = em.find(DbThing.class, 4L).getTime().toLocalTime();
			assertThat("how the hell did that happen???", time1.toString(), is("21:30"));
		}

		{
			// now double check by pretending the mysql server is running in the same timezone as the system, so
			// the time should not be altered
			setMySQLTimezone("US/Eastern");
			EntityManager em2 = connect();
			final LocalTime time2 = em2.find(DbThing.class, 4L).getTime().toLocalTime();
			assertThat(time2.toString(), is("01:30"));
		}
	}

	private void setMySQLTimezone(String timezone) {
		jdbcUrl = JDBC_URL + "?serverTimezone=" + timezone;
	}

	private void setSystemTimezone(String tz) {
		TimeZone.setDefault(TimeZone.getTimeZone(tz));
	}

	private long givenResultAt1PMInTheSystemTimezone(EntityManager em) {
		return givenResultAt(em, LocalDateTime.of(2020, 1, 1, 13, 0, 0));
	}

	private long givenResultAt(EntityManager em, LocalDateTime dateTime) {
		DbThing r = new DbThing();
		r.setTime(dateTime);
		r.setUrl("http://givenresult.com");
		em.getTransaction().begin();
		em.persist(r);
		em.getTransaction().commit();
		return r.getId();
	}

	private EntityManager connect() {
		return EmfBuilder.forMySQL()
				.setProperty("hibernate.connection.url", jdbcUrl)
				.setProperty("hibernate.connection.user", "testuser")
				//.setProperty("hibernate.show_sql", "true")
				//.setProperty("hibernate.format_sql", "true")
				//.setProperty("hibernate.use_sql_comments", "true")
				.withEntities(DbThing.class)
				.build()
				.createEntityManager();
	}
}

