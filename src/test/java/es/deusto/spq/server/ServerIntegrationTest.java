package es.deusto.spq.server;

import static org.junit.Assert.assertEquals;

import java.util.UUID;

import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Transaction;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.glassfish.grizzly.http.server.HttpServer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import categories.IntegrationTest;
import es.deusto.spq.pojo.Role;
import es.deusto.spq.pojo.UserData;
import es.deusto.spq.server.jdo.User;

@Category(IntegrationTest.class)
public class ServerIntegrationTest {

    private static final PersistenceManagerFactory pmf = JDOHelper
            .getPersistenceManagerFactory("datanucleus.properties");

    private static HttpServer server;
    private WebTarget target;

    @BeforeClass
    public static void prepareTests() throws Exception {
        // start the server
        server = Main.startServer();

        PersistenceManager pm = pmf.getPersistenceManager();
        Transaction tx = pm.currentTransaction();
        try {
            tx.begin();
            pm.makePersistent(new User("my-admin", "my-admin", "my-admin", "my-admin", Role.ADMIN));
            pm.makePersistent(new User("test-student", "test-student", "test-student", "test-student", Role.STUDENT));
            tx.commit();
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            pm.close();
        }
    }

    @Before
    public void setUp() {
        // create the client
        Client c = ClientBuilder.newClient();
        target = c.target(Main.BASE_URI).path("resource");
    }

    @AfterClass
    public static void tearDownServer() throws Exception {
        server.shutdown();

        PersistenceManager pm = pmf.getPersistenceManager();
        Transaction tx = pm.currentTransaction();
        try {
            tx.begin();
            pm.newQuery(User.class).deletePersistentAll();
            tx.commit();
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            pm.close();
        }
    }

    @Test
    public void testRegisterUser() {
        UserData userData = new UserData();
        userData.setLogin(UUID.randomUUID().toString());
        userData.setPassword("1234");

        Response response = target.path("users")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(userData, MediaType.APPLICATION_JSON));

        assertEquals(Family.SERVER_ERROR, response.getStatusInfo().getFamily());
    }

    @Test
    public void testLoginUser() {
        UserData userData = new UserData();
        userData.setLogin("my-admin");
        userData.setPassword("my-admin");
        Response response = target.path("login").queryParam("login", userData.getLogin())
                .queryParam("password", userData.getPassword())
                .request(MediaType.APPLICATION_JSON)
                .get();

        assertEquals(Family.SUCCESSFUL, response.getStatusInfo().getFamily());
    }

    @Test
    public void testUpdateUser() {
        UserData currentUser = new UserData();
        currentUser.setLogin("test-student");
        currentUser.setPassword("test-student2");
        currentUser.setName("test-student2");
        currentUser.setSurname("test-student2");
        currentUser.setRole(Role.STUDENT);

        Response response = target.path("users/" + currentUser.getLogin() + "/update")
                .queryParam("login", "my-admin").queryParam("password", "my-admin")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.entity(currentUser, MediaType.APPLICATION_JSON));

        assertEquals(Family.SUCCESSFUL, response.getStatusInfo().getFamily());

        Response response2 = target.path("users/" + currentUser.getLogin() + "/update")
                .queryParam("login", "my-adminfake").queryParam("password", "my-adminfake")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.entity(currentUser, MediaType.APPLICATION_JSON));

        assertEquals(Family.CLIENT_ERROR, response2.getStatusInfo().getFamily());
    }

    @Test
    public void testDeleteUser() {

        Response response = target.path("users/" + "test-student" + "/delete")
                .queryParam("login", "my-admin").queryParam("password", "my-admin")
                .request(MediaType.APPLICATION_JSON)
                .delete();

        assertEquals(Family.SUCCESSFUL, response.getStatusInfo().getFamily());

        Response response2 = target.path("users/" + "test-studentfake" + "/delete")
                .queryParam("login", "my-admin").queryParam("password", "my-admin")
                .request(MediaType.APPLICATION_JSON)
                .delete();

        assertEquals(Family.CLIENT_ERROR, response2.getStatusInfo().getFamily());

        Response response3 = target.path("users/" + "test-student" + "/delete")
                .queryParam("login", "my-adminfake").queryParam("password", "my-adminfake")
                .request(MediaType.APPLICATION_JSON)
                .delete();

        assertEquals(Family.CLIENT_ERROR, response3.getStatusInfo().getFamily());
    }
}
