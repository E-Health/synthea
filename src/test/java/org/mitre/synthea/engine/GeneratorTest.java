package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedList;
import java.util.List;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.export.Exporter;
import org.mitre.synthea.export.Exporter.SupportedFhirVersion;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.geography.Location;

public class GeneratorTest {

  /**
   * Configure settings across these tests.
   * @throws Exception on test configuration loading errors.
   */
  @BeforeClass
  public static void setup() throws Exception {
    TestHelper.exportOff();
    TestHelper.loadTestProperties();
    Generator.DEFAULT_STATE = Config.get("test_state.default", "Massachusetts");
    Config.set("generate.only_dead_patients", "false");
  }

  /**
   * Configure each test.
   * @throws Exception on configuration error.
   */
  @Before
  public void before() throws Exception {
    Config.set("generate.only_dead_patients", "false");
  }

  @Test
  public void testGeneratorCreatesPeople() throws Exception {
    int numberOfPeople = 1;
    Generator generator = new Generator(numberOfPeople);
    generator.run();
    assertEquals(numberOfPeople, generator.stats.get("alive").longValue());

    numberOfPeople = 3;
    Config.set("generate.default_population", Integer.toString(numberOfPeople));
    generator = new Generator();
    generator.run();
    assertEquals(numberOfPeople, generator.stats.get("alive").longValue());
  }

  @Test
  public void testGenerateWithDatabase() throws Exception {
    int numberOfPeople = 1;
    Config.set("generate.database_type", "in-memory");
    Generator generator = new Generator(numberOfPeople, 0L, 1L);
    Config.set("generate.database_type", "none");
    assertNotNull(generator.database);
    generator.run();
    assertEquals(numberOfPeople, generator.stats.get("alive").longValue());
  }

  @Test
  public void testGenerateWithMetrics() throws Exception {
    int numberOfPeople = 1;
    Config.set("generate.track_detailed_transition_metrics", "true");
    Generator generator = new Generator(numberOfPeople, 0L, 1L);
    Config.set("generate.track_detailed_transition_metrics", "false");
    assertNotNull(generator.metrics);
    generator.run();
    assertEquals(numberOfPeople, generator.stats.get("alive").longValue());
  }

  @Test
  public void testGenerateWithDetailedLogLevel() throws Exception {
    int numberOfPeople = 1;
    Config.set("generate.log_patients.detail", "detailed");
    Generator generator = new Generator(numberOfPeople, 0L, 1L);
    Config.set("generate.log_patients.detail", "simple");
    generator.run();
    assertEquals(numberOfPeople, generator.stats.get("alive").longValue());
  }
  
  @Test
  public void testGenerateOnlyDeadPatients() throws Exception {
    Config.set("generate.only_dead_patients", "true");
    int numberOfPeople = 2;
    Generator generator = new Generator(numberOfPeople);
    generator.run();
    assertEquals(0, generator.stats.get("alive").longValue());
    assertEquals(numberOfPeople, generator.stats.get("dead").longValue());
  }

  @Test
  public void testGenerateOnlyAlivePatients() throws Exception {
    Config.set("generate.only_alive_patients", "true");
    int numberOfPeople = 2;
    Generator generator = new Generator(numberOfPeople);
    generator.run();
    assertEquals(0, generator.stats.get("dead").longValue());
    assertEquals(numberOfPeople, generator.stats.get("alive").longValue());
  }

  @Test
  public void testOnlyAliveAndDead() throws Exception {
    Config.set("generate.only_alive_patients", "true");
    Config.set("generate.only_dead_patients", "true");
    int numberOfPeople = 2;
    Generator generator = new Generator(numberOfPeople);
    generator.run();
    assertEquals("false", Config.get("generate.only_alive_patients"));
    assertEquals("false", Config.get("generate.only_dead_patients"));
  }

  @Test
  public void testGeneratePeopleDefaultLocation() throws Exception {
    int numberOfPeople = 2;
    Generator generator = new Generator(); // intentionally no args
    for (int i = 0; i < numberOfPeople; i++) {
      Person p = generator.generatePerson(i);
      assertEquals(Generator.DEFAULT_STATE, p.attributes.get(Person.STATE));
    }
  }
  
  @Test
  public void testGeneratePeopleByLocation() throws Exception {
    String testStateDefault = Config.get("test_state.default", "Massachusetts");
    String testTownDefault = Config.get("test_town.default", "Bedford");
    String testStateAlt = Config.get("test_state.alternative", "California");
    String testTownAlt = Config.get("test_town.alternative", "South Gate");

    int numberOfPeople = 2;
    Generator.GeneratorOptions opts = new Generator.GeneratorOptions();
    opts.population = numberOfPeople;
    opts.state = testStateAlt;
    opts.city = testTownAlt;
    Generator generator = new Generator(opts);
    Location location = new Location(testStateAlt, testTownAlt);
    List<String> zipCodes = location.getZipCodes(testTownAlt);
    for (int i = 0; i < numberOfPeople; i++) {
      Person p = generator.generatePerson(i);
      assertEquals(testTownAlt, p.attributes.get(Person.CITY));
      assertEquals(testStateAlt, p.attributes.get(Person.STATE));
      assertTrue(zipCodes.contains(p.attributes.get(Person.ZIP)));
    }

    opts = new Generator.GeneratorOptions();
    opts.population = numberOfPeople;
    opts.state = testStateDefault;
    opts.city = testTownDefault;
    generator = new Generator(opts);
    location = new Location(testStateDefault, testTownDefault);
    zipCodes = location.getZipCodes(testTownDefault);
    for (int i = 0; i < numberOfPeople; i++) {
      Person p = generator.generatePerson(i);
      assertEquals(testTownDefault, p.attributes.get(Person.CITY));
      assertEquals(testStateDefault, p.attributes.get(Person.STATE));
      assertTrue(zipCodes.contains(p.attributes.get(Person.ZIP)));
    }
  }
  
  @Test
  public void testDemographicsRetry() throws Exception {
    // confirm that the demographic choices will persist if the first generated patients die
    int numberOfPeople = 4;
    Generator.GeneratorOptions opts = new Generator.GeneratorOptions();
    opts.population = numberOfPeople;
    opts.minAge = 50;
    opts.maxAge = 100;
    Generator generator = new Generator(opts);
    generator.internalStore = new LinkedList<>();
    for (int i = 0; i < numberOfPeople; i++) {
      Person person = generator.generatePerson(i);
      
      // the person returned will be last in the internalStore
      int personIndex = generator.internalStore.size() - 1;
      
      for (int j = personIndex - 1; j >= 0; j--) { //
        Person compare = generator.internalStore.get(j);
        
        // basic demographics should always be exactly the same
        assertEquals(person.attributes.get(Person.CITY), compare.attributes.get(Person.CITY));
        assertEquals(person.attributes.get(Person.RACE), compare.attributes.get(Person.RACE));
        
        long expectedBirthdate;
        
        if (personIndex < 10) {
          // less than 10 attempts were made, so all of them should match exactly
          expectedBirthdate = (long)person.attributes.get(Person.BIRTHDATE);
        } else if (j > 10) {
          // the person we got back (potentially) has the changed target birthdate
          // and so would any with index > 10
          // so these should match exactly
          expectedBirthdate = (long)person.attributes.get(Person.BIRTHDATE);
        } else {
          // the person we got back (potentially) has the changed target birthdate
          // but any with index < 10 might not
          // in this case, ensure the first 10 match index 0 (which the loop will take care of)
          expectedBirthdate = (long)generator.internalStore.get(0).attributes.get(Person.BIRTHDATE);
        }
        
        assertEquals(expectedBirthdate, (long)compare.attributes.get(Person.BIRTHDATE));
      }
      
      generator.internalStore.clear();
    }
  }
  
  @Test
  public void testGenerateRecordQueue() throws Exception {
    int numberOfPeople = 10;
    Generator.GeneratorOptions opts = new Generator.GeneratorOptions();
    opts.population = numberOfPeople;
    Exporter.ExporterRuntimeOptions ero = new Exporter.ExporterRuntimeOptions();
    ero.enableQueue(SupportedFhirVersion.STU3);

    // Create and start generator thread
    Generator generator = new Generator(opts, ero);
    Thread generateThread = new Thread() {
      public void run() {
        generator.run();
      }
    };
    generateThread.start();

    int count = 0;
    while (generateThread.isAlive()) {
      ero.getNextRecord();
      ++count;

      if (count == numberOfPeople) {
        // Break out if we have gotten enough records.
        break;
      }
    }

    if (count < numberOfPeople) {
      // Generator thread terminated but we have not gotten enough records yet. Check queue.
      if (!ero.isRecordQueueEmpty()) {      
        ero.getNextRecord();
        ++count;
      }
    }

    assertEquals(numberOfPeople, count);

    generateThread.interrupt();
  }
}
