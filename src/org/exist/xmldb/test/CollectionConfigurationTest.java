package org.exist.xmldb.test;

import junit.framework.TestCase;

import org.exist.collections.CollectionConfiguration;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.IndexQueryService;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

public class CollectionConfigurationTest extends TestCase {

    private final static String URI = "xmldb:exist://";

    private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";

    private final static String TEST_COLLECTION = "testIndexConfiguration";

    private final static String DOCUMENT_NAME = "test.xml";

    private final static String DOCUMENT_CONTENT = "<test>" + "<a>001</a>"
            + "<a>01</a>" + "<a>1</a>" + "<b>001</b>" + "<b>01</b>"
            + "<b>1</b>" + "</test>";

    private String CONFIG1 = "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">"
            + "  <index>"
            + "    <create qname=\"a\" type=\"xs:integer\"/>"
            + "    <create qname=\"b\" type=\"xs:string\"/>"
            + "  </index>"
            + "</collection>";

    private String CONFIG2 = "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">"
            + "  <index>"
            + "    <create qname=\"b\" type=\"xs:integer\"/>"
            + "    <create qname=\"a\" type=\"xs:string\"/>"
            + "  </index>"
            + "</collection>";

    private Collection testCollection;
    private String configurationFileName;

    protected void setUp() {
        try {
            // initialize driver
            Class cl = Class.forName(DRIVER);
            Database database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            Collection root = DatabaseManager.getCollection(URI + DBBroker.ROOT_COLLECTION, "admin", null);
            CollectionManagementService service = (CollectionManagementService) root
                    .getService("CollectionManagementService", "1.0");
            testCollection = service.createCollection(TEST_COLLECTION);
            assertNotNull(testCollection);
            configurationFileName = null;
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    protected void tearDown() {
        try {
           
            Collection root = DatabaseManager.getCollection(URI + DBBroker.ROOT_COLLECTION, "admin", null);
            CollectionManagementService service = (CollectionManagementService) root
                    .getService("CollectionManagementService", "1.0");
            service.removeCollection(TEST_COLLECTION);
            testCollection = null;
            
            //Removes the collection config collection *manually*            
            if (configurationFileName == null)
                configurationFileName = CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE;
            String configPath = CollectionConfigurationManager.CONFIG_COLLECTION
                    + DBBroker.ROOT_COLLECTION + "/" + TEST_COLLECTION;                    
            System.out.println("Manually removing '" + configPath + "/" + configurationFileName + "'");
            Collection configColl = DatabaseManager.getCollection(URI + configPath, "admin", null);  
            assertNotNull(configColl);
            configColl.removeResource(configColl.getResource(configurationFileName));
            //TODO remove the config collection as well ?
            
            DatabaseInstanceManager mgr = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
            mgr.shutdown();
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }


     public void testCollectionConfigurationService1() { 
         ResourceSet result; 
         try {
             //Configure collection automatically
             IndexQueryService idxConf = (IndexQueryService)
             testCollection.getService("IndexQueryService", "1.0");
             idxConf.configureCollection(CONFIG1);
 
             //... then index document 
             XMLResource doc = (XMLResource)
             testCollection.createResource(DOCUMENT_NAME, "XMLResource" );
             doc.setContent(DOCUMENT_CONTENT); testCollection.storeResource(doc);
     
             XPathQueryService service = (XPathQueryService)
             testCollection.getService("XPathQueryService", "1.0");
    
             //3 numeric values 
             result = service.query("util:qname-index-lookup(xs:QName(\"a\"), 1 ) "); 
             assertEquals(3, result.getSize()); 
             //... but 1 string value 
             result = service.query("util:qname-index-lookup(xs:QName(\"b\"), \"1\" ) "); 
             assertEquals(1, result.getSize()); }
         catch(Exception e) { 
             fail(e.getMessage());             
         }
    }
     

    public void testCollectionConfigurationService2() {
        ResourceSet result;
        try {
            // Add document....
            XMLResource doc = (XMLResource) testCollection.createResource(
                    DOCUMENT_NAME, "XMLResource");
            doc.setContent(DOCUMENT_CONTENT);
            testCollection.storeResource(doc);

            // ... then configure collection automatically
            IndexQueryService idxConf = (IndexQueryService) testCollection
                    .getService("IndexQueryService", "1.0");
            idxConf.configureCollection(CONFIG1);

            XPathQueryService service = (XPathQueryService) testCollection
                    .getService("XPathQueryService", "1.0");

            // No numeric values because we have no index
            result = service.query("util:qname-index-lookup( xs:QName(\"a\"), 1 ) ");
            assertEquals(0, result.getSize());
            // No string value because we have no index
            result = service.query("util:qname-index-lookup( xs:QName(\"b\"), \"1\" ) ");
            assertEquals(0, result.getSize());

            // ...let's activate the index
            idxConf.reindexCollection();            

            // 3 numeric values
            result = service.query("util:qname-index-lookup( xs:QName(\"a\"), 1 ) ");
            assertEquals(3, result.getSize());
            // ... but 1 string value
            result = service.query("util:qname-index-lookup( xs:QName(\"b\"), \"1\" ) ");
            assertEquals(1, result.getSize());

        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    public void testCollectionConfigurationService3() { 
        ResourceSet result; 
        try {
            //Configure collection *manually*            
            String configPath = CollectionConfigurationManager.CONFIG_COLLECTION
                    + DBBroker.ROOT_COLLECTION + "/" + TEST_COLLECTION;
            configurationFileName = CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE;
            System.out.println("Manually adding '" + configPath + "/"
                    + configurationFileName + "'");
            Collection configColl = DatabaseManager.getCollection(URI + configPath, "admin", null);             
            assertNotNull(configColl);
            Resource res = configColl.createResource(configurationFileName, "XMLResource");
            assertNotNull(res);
            res.setContent(CONFIG1);            
            configColl.storeResource(res);             

            //... then index document 
            XMLResource doc = (XMLResource)
            testCollection.createResource(DOCUMENT_NAME, "XMLResource" );
            doc.setContent(DOCUMENT_CONTENT); testCollection.storeResource(doc);
    
            XPathQueryService service = (XPathQueryService)
            testCollection.getService("XPathQueryService", "1.0");
   
            //3 numeric values 
            result = service.query("util:qname-index-lookup(xs:QName(\"a\"), 1 ) "); 
            assertEquals(3, result.getSize()); 
            //... but 1 string value 
            result = service.query("util:qname-index-lookup(xs:QName(\"b\"), \"1\" ) "); 
            assertEquals(1, result.getSize()); }
        catch(Exception e) { 
            fail(e.getMessage());             
        }
   }
    

   public void testCollectionConfigurationService4() {
       ResourceSet result;
       try {
           // Add document....
           XMLResource doc = (XMLResource) testCollection.createResource(
                   DOCUMENT_NAME, "XMLResource");
           doc.setContent(DOCUMENT_CONTENT);
           testCollection.storeResource(doc);

           // ... then configure collection *manually*           
           String configPath = CollectionConfigurationManager.CONFIG_COLLECTION
                   + DBBroker.ROOT_COLLECTION + "/" + TEST_COLLECTION;
           configurationFileName = CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE;
           System.out.println("Manually adding '" + configPath + "/"
                   + configurationFileName + "'");
           Collection configColl = DatabaseManager.getCollection(URI + configPath, "admin", null);            
           Resource res = configColl.createResource(configurationFileName, "XMLResource");
           assertNotNull(res);
           res.setContent(CONFIG1);            
           configColl.storeResource(res); 

           XPathQueryService service = (XPathQueryService) testCollection
                   .getService("XPathQueryService", "1.0");

           // No numeric values because we have no index
           result = service.query("util:qname-index-lookup( xs:QName(\"a\"), 1 ) ");
           assertEquals(0, result.getSize());
           // No string value because we have no index
           result = service.query("util:qname-index-lookup( xs:QName(\"b\"), \"1\" ) ");
           assertEquals(0, result.getSize());

           // ...let's activate the index
           IndexQueryService idxConf = (IndexQueryService) 
               testCollection.getService("IndexQueryService", "1.0");
           idxConf.reindexCollection();            

           // 3 numeric values
           result = service.query("util:qname-index-lookup( xs:QName(\"a\"), 1 ) ");
           assertEquals(3, result.getSize());
           // ... but 1 string value
           result = service.query("util:qname-index-lookup( xs:QName(\"b\"), \"1\" ) ");
           assertEquals(1, result.getSize());

       } catch (Exception e) {
           fail(e.getMessage());
       }
   } 
   
   public void testCollectionConfigurationService5() {
       ResourceSet result;
       try {
           //Configure collection *manually*           
           String configPath = CollectionConfigurationManager.CONFIG_COLLECTION
                   + DBBroker.ROOT_COLLECTION + "/" + TEST_COLLECTION;
           configurationFileName = "foo" + CollectionConfiguration.COLLECTION_CONFIG_SUFFIX;
           System.out.println("Manually adding '" + configPath + "/"
                   + configurationFileName + "'");
           Collection configColl = DatabaseManager.getCollection(URI + configPath, "admin", null);            
           Resource res = configColl.createResource(configurationFileName, "XMLResource");
           assertNotNull(res);
           res.setContent(CONFIG1);            
           configColl.storeResource(res);            
           
           // ... then configure collection automatically
           IndexQueryService idxConf = (IndexQueryService) testCollection
                   .getService("IndexQueryService", "1.0");
           idxConf.configureCollection(CONFIG1);           
           
           // Add document....
           XMLResource doc = (XMLResource) testCollection.createResource(
                   DOCUMENT_NAME, "XMLResource");
           doc.setContent(DOCUMENT_CONTENT);
           testCollection.storeResource(doc);

           XPathQueryService service = (XPathQueryService) testCollection
                   .getService("XPathQueryService", "1.0");

           //our config file
           result = service.query("xmldb:get-child-resources('" +
                   CollectionConfigurationManager.CONFIG_COLLECTION +
                   DBBroker.ROOT_COLLECTION + "/" + TEST_COLLECTION +
                   "')");  
           assertEquals(configurationFileName, result.getResource(0).getContent());           
           
           // 3 numeric values
           result = service.query("util:qname-index-lookup( xs:QName(\"a\"), 1 ) ");
           assertEquals(3, result.getSize());
           // ... but 1 string value
           result = service.query("util:qname-index-lookup( xs:QName(\"b\"), \"1\" ) ");
           assertEquals(1, result.getSize());           

       } catch (Exception e) {
           fail(e.getMessage());
       }
   } 
   
   public void testCollectionConfigurationService6() {
       ResourceSet result;
       try {
           // Add document....
           XMLResource doc = (XMLResource) testCollection.createResource(
                   DOCUMENT_NAME, "XMLResource");
           doc.setContent(DOCUMENT_CONTENT);
           testCollection.storeResource(doc);

           //... then configure collection *manually* 
           String configPath = CollectionConfigurationManager.CONFIG_COLLECTION
                   + DBBroker.ROOT_COLLECTION + "/" + TEST_COLLECTION;
           configurationFileName = "foo" + CollectionConfiguration.COLLECTION_CONFIG_SUFFIX;
           System.out.println("Manually adding '" + configPath + "/"
                   + configurationFileName + "'");
           Collection configColl = DatabaseManager.getCollection(URI + configPath, "admin", null);            
           Resource res = configColl.createResource(configurationFileName, "XMLResource");
           assertNotNull(res);
           res.setContent(CONFIG1);            
           configColl.storeResource(res); 
           
           //... then configure collection automatically 
           IndexQueryService idxConf = (IndexQueryService)
           testCollection.getService("IndexQueryService", "1.0");           
           idxConf.configureCollection(CONFIG1); 
           
           XPathQueryService service = (XPathQueryService) testCollection
           .getService("XPathQueryService", "1.0"); 
           
           //our config file
           result = service.query("xmldb:get-child-resources('" +
                   CollectionConfigurationManager.CONFIG_COLLECTION +
                   DBBroker.ROOT_COLLECTION + "/" + TEST_COLLECTION +
                   "')");  
           assertEquals(configurationFileName, result.getResource(0).getContent());

           // No numeric values because we have no index
           result = service.query("util:qname-index-lookup( xs:QName(\"a\"), 1 ) ");
           assertEquals(0, result.getSize());
           // No string value because we have no index
           result = service.query("util:qname-index-lookup( xs:QName(\"b\"), \"1\" ) ");
           assertEquals(0, result.getSize());

           // ...let's activate the index          
           idxConf.reindexCollection(); 
           
           //WARNING : the code hereafter used to *not* work whereas 
           //testCollectionConfigurationService4 did. 
           //Adding confMgr.invalidateAll(getName()); in Collection.storeInternal solved the problem
           //Strange case that needs investigations... -pb
           
           // 3 numeric values
           result = service.query("util:qname-index-lookup( xs:QName(\"a\"), 1 ) ");
           assertEquals(3, result.getSize());
           // ... but 1 string value
           result = service.query("util:qname-index-lookup( xs:QName(\"b\"), \"1\" ) ");
           assertEquals(1, result.getSize());           
                 

       } catch (Exception e) {
           fail(e.getMessage());
       }
   } 
   
   public void testCollectionConfigurationService7() {
       ResourceSet result;
       boolean exceptionThrown = false;
       String message;
       try {
           //Configure collection *manually* 
           String configPath = CollectionConfigurationManager.CONFIG_COLLECTION
                   + DBBroker.ROOT_COLLECTION + "/" + TEST_COLLECTION;
           configurationFileName = "foo" + CollectionConfiguration.COLLECTION_CONFIG_SUFFIX;
           System.out.println("Manually adding '" + configPath + "/"
                   + configurationFileName + "'");
           Collection configColl = DatabaseManager.getCollection(URI + configPath, "admin", null);            
           Resource res = configColl.createResource(configurationFileName, "XMLResource");
           assertNotNull(res);
           res.setContent(CONFIG1);            
           configColl.storeResource(res);            

           try {
           
               //Reconfigure collection *manually* 
               configPath = CollectionConfigurationManager.CONFIG_COLLECTION
                       + DBBroker.ROOT_COLLECTION + "/" + TEST_COLLECTION;   
               configurationFileName = "bar" + CollectionConfiguration.COLLECTION_CONFIG_SUFFIX;
               System.out.println("Manually adding '" + configPath + "/"
                       + configurationFileName + "'");
               configColl = DatabaseManager.getCollection(URI + configPath, "admin", null);            
               res = configColl.createResource(configurationFileName, "XMLResource");
               assertNotNull(res);
               res.setContent(CONFIG1);            
               configColl.storeResource(res);  
           } catch (XMLDBException e) {
                exceptionThrown = true;
                message = e.getMessage();
            }
            assertTrue(configurationFileName, exceptionThrown);
            configurationFileName = "foo" + CollectionConfiguration.COLLECTION_CONFIG_SUFFIX;
           
       } catch (Exception e) {
           fail(e.getMessage());
       }
   }    


// TODO : 2 manual configurations (exception because foo.xonf/bar.xconf)

    public static void main(String[] args) {
        junit.textui.TestRunner.run(CollectionConfigurationTest.class);        
    }
}