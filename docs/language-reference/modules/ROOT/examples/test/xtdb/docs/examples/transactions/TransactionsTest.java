package xtdb.docs.examples.transactions;

import org.junit.*;

import xtdb.api.*;
import xtdb.api.tx.*;

import java.util.Date;
import java.time.Duration;

import static xtdb.api.TestUtils.*;
import static org.junit.Assert.*;
import static xtdb.api.XtdbDocument.build;

// tag::creating-0[]
import static xtdb.api.tx.Transaction.buildTx;
// end::creating-0[]

public class TransactionsTest {
    private static final String documentId = "foo";
    private static XtdbDocument document;
    private static XtdbDocument document1;
    private static XtdbDocument document2;
    private static XtdbDocument document3;

    private static Date beforeAll;
    private static Date validTime1;
    private static Date validTime2;
    private static Date endValidTime;

    private static final String documentId1 = "foo-1";
    private static final String documentId2 = "foo-2";
    private static final String documentId3 = "foo-3";

    private final IXtdb node = IXtdb.startNode();

    private static XtdbDocument createDocument(int version) {
        return XtdbDocument.create(documentId).plus("version", version);
    }

    @BeforeClass
    public static void beforeClass() {
        //We are using individual variables so the examples are clearer.
        document = XtdbDocument.create(documentId);
        document1 = createDocument(1);
        document2 = createDocument(2);
        document3 = createDocument(3);

        beforeAll = date(-11000000);
        validTime1 = date(-10000000);
        validTime2 = date(-9000000);
        endValidTime = date(-8000000);
    }

    @AfterClass
    public static void afterClass() {
        document = null;
        document1 = null;
        document2 = null;
        document3 = null;

        beforeAll = null;
        validTime1 = null;
        validTime2 = null;
        endValidTime = null;
    }

    @After
    public void after() {
        close(node);
    }

    @Test
    public void creatingTransactions() {
        Transaction fromBuilder =
                // tag::creating-1[]
                Transaction.builder()
                        .put(document)
                        .build();
        // end::creating-1[]

        Transaction fromConsumer =
                // tag::creating-2[]
                buildTx(tx -> {
                    tx.put(document);
                });
        // end::creating-2[]

        assertEquals(fromBuilder, fromConsumer);
    }

    @Test
    public void usingTransactions() {
        Transaction transaction = buildTx(tx -> tx.put(document));

        // tag::using-0[]
        node.submitTx(transaction);
        // end::using-0[]

        // tag::using-1[]
        node.submitTx(buildTx(tx -> {
            tx.put(document);
        }));
        // end::using-1[]

        sync(node);

        assertDocument(document);
    }

    @Test
    public void putOperation() {
        // tag::put[]
        node.submitTx(buildTx(tx -> {
            tx.put(document1); // <1>
            tx.put(document2, validTime1); // <2>
            tx.put(document3, validTime2, endValidTime); // <3>
        }));
        // end::put[]

        sync(node);

        assertDocument(document1);
        assertDocument(document2, validTime1);
        assertDocument(document3, validTime2);
        assertDocument(document2, endValidTime);
    }

    @Test
    public void deleteOperation() {
        node.submitTx(buildTx(tx -> {
            tx.put(XtdbDocument.create(documentId1), beforeAll);
            tx.put(XtdbDocument.create(documentId2), beforeAll);
            tx.put(XtdbDocument.create(documentId3), beforeAll);
        }));

        sync(node);

        // tag::delete[]
        node.submitTx(buildTx(tx -> {
            tx.delete(documentId1); // <1>
            tx.delete(documentId2, validTime1); // <2>
            tx.delete(documentId3, validTime2, endValidTime); // <3>
        }));
        // end::delete[]

        sync(node);

        assertTrue(exists(documentId1, beforeAll));
        assertTrue(exists(documentId2, beforeAll));
        assertTrue(exists(documentId3, beforeAll));

        assertTrue(exists(documentId1, validTime1));
        assertFalse(exists(documentId2, validTime1));
        assertTrue(exists(documentId3, validTime1));

        assertTrue(exists(documentId1, validTime2));
        assertFalse(exists(documentId2, validTime2));
        assertFalse(exists(documentId3, validTime2));

        assertTrue(exists(documentId1, endValidTime));
        assertFalse(exists(documentId2, endValidTime));
        assertTrue(exists(documentId3, endValidTime));

        assertFalse(exists(documentId1));
        assertFalse(exists(documentId2));
        assertTrue(exists(documentId3));
    }

    @Test
    public void matchTest() {
        node.submitTx(buildTx(tx -> {
            tx.put(document, beforeAll);
        }));

        sync(node);

        // tag::match[]
        node.submitTx(buildTx(tx -> {
            tx.match(document1); // <1>
            tx.match(document2, validTime1); // <2>

            tx.matchNotExists(documentId1); // <3>
            tx.matchNotExists(documentId2, validTime2); // <4>

            tx.put(document3); // <5>
        }));
        // end::match[]

        sync(node);

        assertEquals(document, node.db().entity(documentId));
    }

    @Test
    public void evict() {
        node.submitTx(buildTx(tx -> {
            tx.put(document1, beforeAll);
            tx.put(document2, validTime1);
            tx.put(document3, validTime2, endValidTime);
        }));

        sync(node);

        assertDocument(document1, beforeAll);
        assertDocument(document2, validTime1);
        assertDocument(document3, validTime2);
        assertDocument(document2, endValidTime);
        assertDocument(document2);

        // tag::evict[]
        node.submitTx(buildTx(tx -> {
            tx.evict(documentId);
        }));
        // end::evict[]

        sync(node);

        assertFalse(exists(beforeAll));
        assertFalse(exists(validTime1));
        assertFalse(exists(validTime2));
        assertFalse(exists(endValidTime));
        assertFalse(exists());
    }

    @Test
    public void functions() {
        // tag::fn-put[]
        TransactionInstant ti = node.submitTx(buildTx(tx -> {
            tx.put(XtdbDocument.createFunction("incAge",
            "(fn [ctx eid] (let [db (xtdb.api/db ctx) entity (xtdb.api/entity db eid)] [[:xtdb.api/put (update entity :age inc)]]))"));
        }));
        // end::fn-put[]

        node.awaitTx(ti, null);

        ti = node.submitTx(buildTx(tx -> {
            tx.put(XtdbDocument.create("ivan").plus("age", 0L));
        }));

        node.awaitTx(ti, null);

        ti =
        // tag::fn-use[]
        node.submitTx(buildTx(tx -> {
            tx.invokeFunction("incAge", "ivan");
        }));
        // end::fn-use[]

        node.awaitTx(ti, null);

        XtdbDocument compare = XtdbDocument.create("ivan").plus("age", 1L);

        assertEquals(compare, node.db().entity("ivan"));
    }

    @Test
    public void transactionInstant() {
        // tag::ti[]
        TransactionInstant ti = node.submitTx(buildTx(tx -> {
            tx.put(XtdbDocument.create("Ivan"));
        }));

        // This will be null because the transaction won't have been indexed yet
        assertNull(node.db().entity("Ivan"));

        // Here we will wait until it has been indexed
        node.awaitTx(ti, Duration.ofSeconds(5));

        // And now our new document will be in the DB snapshot
        assertNotNull(node.db().entity("Ivan"));
        // end::ti[]
    }

    @Test
    public void documents() {
        XtdbDocument fromBuilder =
        // tag::doc-builder[]
        XtdbDocument.builder("pablo-picasso")
            .put("name", "Pablo")
            .put("lastName", "Picasso")
            .build();
        // end::doc-builder[]

        XtdbDocument fromConsumer =
        // tag::doc-consumer[]
        build("pablo-picasso", doc -> {
            doc.put("name", "Pablo");
            doc.put("lastName", "Picasso");
        });
        // end::doc-consumer[]

        XtdbDocument direct =
        // tag::doc-direct[]
        XtdbDocument.create("pablo-picasso")
            .plus("name", "Pablo")
            .plus("lastName", "Picasso");
        // end::doc-direct[]

        assertEquals(fromBuilder, fromConsumer);
        assertEquals(fromBuilder, direct);
    }

    @Test
    public void withTransactions() {
        // tag::with-tx[]
        TransactionInstant ti = node.submitTx(buildTx(tx -> {
            tx.put(XtdbDocument.create("Ivan"));
        }));

        awaitTx(node, ti);

        IXtdbDatasource db = node.db();

        assertNotNull(db.entity("Ivan"));
        assertNull(db.entity("Petr"));

        IXtdbDatasource speculativeDb = db.withTx(buildTx(tx -> {
            tx.put(XtdbDocument.create("Petr"));
        }));

        // Petr is in our speculative db
        assertNotNull(speculativeDb.entity("Ivan"));
        assertNotNull(speculativeDb.entity("Petr"));

        // We haven't impacted our original db
        assertNotNull(db.entity("Ivan"));
        assertNull(db.entity("Petr"));

        // Nor have we impacted our node
        assertNotNull(node.db().entity("Ivan"));
        assertNull(node.db().entity("Petr"));
        // end::with-tx[]
    }

    private void assertDocument(XtdbDocument document) {
        assertDocument(document, node.db());
    }

    private void assertDocument(XtdbDocument document, Date validTime) {
        assertDocument(document, node.db(validTime));
    }

    private void assertDocument(XtdbDocument document, IXtdbDatasource db) {
        assertEquals(document, db.entity(documentId));
    }

    private boolean exists() {
        return exists(documentId);
    }

    private boolean exists(Date validTime) {
        return exists(documentId, validTime);
    }

    private boolean exists(Object documentId) {
        return exists(documentId, node.db());
    }

    private boolean exists(Object documentId, Date validTime) {
        return exists(documentId, node.db(validTime));
    }

    private boolean exists(Object documentId, IXtdbDatasource db) {
        return db.entity(documentId) != null;
    }
}
