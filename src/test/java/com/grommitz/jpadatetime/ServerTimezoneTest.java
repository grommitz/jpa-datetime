package com.grommitz.jpadatetime;

import ch.vorburger.exec.ManagedProcessException;
import ch.vorburger.mariadb4j.DB;
import ch.vorburger.mariadb4j.DBConfiguration;
import ch.vorburger.mariadb4j.DBConfigurationBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.TimeZone;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ServerTimezoneTest {

	private static final Logger logger = LoggerFactory.getLogger(ServerTimezoneTest.class.getName());
	private TimeZone tz;
	private DB db;
	public static final String JDBC_URL = "jdbc:mysql://localhost:4406/testdb";
	private String jdbcUrl = JDBC_URL;

	@BeforeEach
	void startDB() throws ManagedProcessException {
		tz = TimeZone.getDefault();
		// Note the Maria server is always in UTC regardless of the system time soze
		DBConfiguration config = DBConfigurationBuilder.newBuilder()
				.setPort(4406)
				.build();
		db = DB.newEmbeddedDB(config);
		db.start();
		db.source("setup.sql");
	}

	@AfterEach
	void tearDown() throws ManagedProcessException {
		db.stop();
		TimeZone.setDefault(tz);
	}

	@Test
	void serverDefaultTzTest() {
		setSystemTimezone("Europe/Paris"); // UTC +2:00 in summer
		EntityManager em = connect();
		MariaResult r = em.find(MariaResult.class, 2L); // 14:00 in the server's local time, ie UTC
		LocalTime time = r.getDate().toLocalTime();
		System.out.println(time);
		assertThat(time, is(LocalTime.of(16, 0)));
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

		MariaResult r = em.find(MariaResult.class, 1L); // 13:00 in the server's local time
		LocalTime time = r.getDate().toLocalTime();

		logger.info("MySQL timezone              : Europe/London");
		logger.info("JVM timezone                : {}", TimeZone.getDefault().getID());
		logger.info("Time stored in the database : 13:00");
		logger.info("Time at the JVM's timezone  : {}", time);

		assertThat("Time in " + TimeZone.getDefault().getID() + " for 1pm in Europe/London should be " + expectedTime,
				time.toString(), is(expectedTime));
	}

	@ParameterizedTest
	@CsvSource({"Europe/London", "US/Eastern", "Asia/Shanghai"})
	@DisplayName("Times are converted to the MySQL server's timezone on write")
	void writeTest(String timezone) {

		setSystemTimezone("Europe/Paris");
		setMySQLTimezone(timezone);

		EntityManager em = connect();
		long id = givenResultAt1PMInTheSystemTimezone(em);

		MariaResult r = em.find(MariaResult.class, id);
		LocalTime time = r.getDate().toLocalTime();

		logger.info("MySQL timezone              : {}", timezone);
		logger.info("JVM timezone                : {}", TimeZone.getDefault().getID());
		logger.info("Time stored in the database : 13:00");
		logger.info("Time at the JVM's timezone  : {}", time);

		assertThat("Time in " + TimeZone.getDefault() + " for 1pm in " + timezone + " should be 13:00",
				time, is(LocalTime.of(13, 0)));

		jdbcUrl = JDBC_URL;
		EntityManager em2 = connect();
		r = em.find(MariaResult.class, id);
		System.out.println("without serverTimezone: " + r.getDate());

	}

	/*
	 * In this test we keep the system timezone constant but we move the MySQL server around by setting
	 * serverTimezone=...
	 */
	@ParameterizedTest
	@CsvSource({"Europe/London, 14:00", "US/Eastern, 19:00", "Europe/Paris, 13:00"})
	@DisplayName("Local DB times to be converted to the system's timezone")
	void readConvertsTimes(String timezone, String eta) {

		setSystemTimezone("Europe/Paris");
		setMySQLTimezone(timezone);

		// built a connection string & explicitly tell the driver the timezone of the MySQL server. It will then
		// automatically convert it to the time our system time
		//String jdbcUrl = "jdbc:mysql://localhost:4406/testdb?serverTimezone=" + timezone;
		EntityManager em = connect();

		// this was inserted in the setup.sql script
		MariaResult r = em.find(MariaResult.class, 1L);
		System.out.println(r.getDate());
		String timePart = r.getDate().toLocalTime().toString();

		logger.info("MySQL timezone              : {}", timezone);
		logger.info("JVM timezone                : {}", TimeZone.getDefault().getID());
		logger.info("Time stored in the database : 13:00");
		logger.info("Time at the JVM's timezone  : {}", r.getDate().toLocalTime());

		assertThat("Time in " + TimeZone.getDefault() + " for 1pm in " + timezone + " should be " + eta,
				timePart, is(eta));
	}

	@Test
	void change() {
		setSystemTimezone("US/Eastern");

		EntityManager em = connect();
		LocalTime time0 = em.find(MariaResult.class, 1L).getDate().toLocalTime();
		System.out.println(time0);

		setMySQLTimezone("US/Eastern");
		em = connect();
		LocalTime time1 = em.find(MariaResult.class, 1L).getDate().toLocalTime();
		System.out.println(time1);

		if (!time1.equals(time0)) {
			fail("Times have been changed my friend");
		}
	}

	@Test
	void badTimeInSpringJumpForward() {

		EntityManager em = connect();
		LocalTime time0 = em.find(MariaResult.class, 3L).getDate().toLocalTime();
		System.out.println(time0);

	}

	private void setMySQLTimezone(String timezone) {
		jdbcUrl = jdbcUrl + "?serverTimezone=" + timezone;
	}

	private void setSystemTimezone(String tz) {
		TimeZone.setDefault(TimeZone.getTimeZone(tz));
	}

	private long givenResultAt1PMInTheSystemTimezone(EntityManager em) {
		MariaResult r = new MariaResult();
		r.setDate(LocalDateTime.of(2020, 1, 1, 13, 0, 0));
		r.setUrl("http://1pm.com");
		em.getTransaction().begin();
		em.persist(r);
		em.getTransaction().commit();
		return r.getId();
	}

	private EntityManager connect() {
		return EmfBuilder.forMySQL()
				.setProperty("hibernate.connection.url", jdbcUrl)
				.withEntities(MariaResult.class)
				.build()
				.createEntityManager();
	}
}

