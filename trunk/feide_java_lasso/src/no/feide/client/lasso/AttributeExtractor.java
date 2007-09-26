package no.feide.client.lasso;

import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.entrouvert.lasso.NodeList;
import com.entrouvert.lasso.Saml2Assertion;
import com.entrouvert.lasso.Saml2Attribute;
import com.entrouvert.lasso.Saml2AttributeStatement;
import com.entrouvert.lasso.Samlp2Response;

/**
 * This class extracts attributes from a Samlp2Response node. You should use the static
 * extractAttributes function.
 */
class AttributeExtractor {

    /**
     * The extracted attributes.
     */
    private final Map<String, Attribute> attributes;

    /**
     * The separator which separates multiple values in an AttributeValue node.
     */
    private final String attributeSeparator;

    /**
     * This constructor extracts attributes from a SAML2 response stored in a Samlp2Response
     * element. The attributes are stored in the private attributes value.
     * @param config 
     * 
     * @param response
     * @throws ServletException
     */
    private AttributeExtractor(Config config, Samlp2Response response) throws ServletException {
        this.attributes = new HashMap<String, Attribute>();
        this.attributeSeparator = config.getAttributeSeparator();

        // Lasso doesn't currently validate more than the first assertion-element. Therefore we
        // throw an exception if more than one assertion is included in the reply.
        if(response.getAssertion().length() > 1) {
            throw new ServletException("More than one assertion in SAML2 response.");            
        }

        NodeList nl = response.getAssertion();
        for(int i = 0; i < nl.length(); i++) {
            Saml2Assertion assertion = (Saml2Assertion)nl.getItem(i);
            this.handleAssertion(assertion);
        }
    }

    /**
     * Handles a SAML2 Assertion node.
     * 
     * @param assertion the SAML2 Assertion node.
     */
    private void handleAssertion(Saml2Assertion assertion) {
        NodeList nl = assertion.getAttributeStatement();
        for(int i = 0; i < nl.length(); i++) {
            Saml2AttributeStatement attributeStatement = (Saml2AttributeStatement)nl.getItem(i);
            this.handleAttributeStatement(attributeStatement);
        }
    }


    /**
     * Handles a SAML2 AttributeStatement node.
     *
     * @param attributeStatement the AttributeStatement node.
     */
    private void handleAttributeStatement(Saml2AttributeStatement attributeStatement) {
        NodeList nl = attributeStatement.getAttribute();
        for(int i = 0; i < nl.length(); i++) {
            Saml2Attribute attribute = (Saml2Attribute)nl.getItem(i);
            this.handleAttribute(attribute);
        }
    }

    /**
     * Handles a SAML2 Attribute node.
     *
     * @param attribute the SAML2 Attribute node.
     */
    private void handleAttribute(Saml2Attribute attribute) {
        String name = attribute.getName();
        Attribute attributeStore = this.findAttribute(name);

        List<String> values = this.getAttributeValues(attribute);
        for(String value : values) {
            this.addFeideAttributeValue(attributeStore, value);
        }
    }

    /**
     * Parses a FEIDE attribute value, and stores it in an Attribute object.
     * FEIDE attributes are stored as a string with base64 encoded strings separated by '_'.
     * The strings are UTF8-encoded.
     *
     * Example: c3R1ZGVudA==_bWVtYmVy
     *
     * @param attributeStore Attribute object where we should store the values we find.
     * @param value the string with the encoded values.
     */
    private void addFeideAttributeValue(Attribute attributeStore, String value) {
        // Split into multiple values at '_'.
        String[] values = value.split(this.attributeSeparator);
        for(String v : values) {
            try {
                attributeStore.addValue(new String(Base64.decode(v), "UTF-8"));
            } catch(UnsupportedEncodingException e) {
                throw new RuntimeException("This java implementation doesn't implement the UTF-8 encoding.", e);
            }
        }
    }

    /**
     * Extracts the string value of every AttributeValue element in a SAML2 Attribute element.
     * The values are returned in a List.
     *
     * This function only accepts attribute values which are string data. Other types of data will
     * result in a RuntimeException.
     *
     * @param attribute Saml2Attribute object which we should extract values from.
     * @return list of attribute values in the Saml2Attribute object.
     */
    private List<String> getAttributeValues(Saml2Attribute attribute) {
        List<String> values = new ArrayList<String>();

        /*
         * Currently, there is no way to extract the AttributeValue from the Java binding to
         * the lasso library. Instead, we serialize the Saml2Attribute object, and parses the
         * string. Serialization results pieces of XML similar to the following:
         * <saml:Attribute xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" Name="eduPersonAffiliation">
         *   <saml:AttributeValue>c3R1ZGVudA==_bWVtYmVy</saml:AttributeValue>
         * </saml:Attribute>
         */

        // Dump the attribute and build a DOM Document object from it.
        Document d = this.parseXML(attribute.dump());

        // The first child will be a saml:Attribute node.
        Node attrNode = d.getFirstChild();
        if(!"saml:Attribute".equals(attrNode.getNodeName())) {
            throw new RuntimeException("First node wasn't a saml:Attribute node. Was: " + attrNode.getNodeName());
        }

        // The saml:Attribute will contain one or more saml:AttributeValue nodes. The nodes
        // are surrounded by whitespace.
        for(Node n = attrNode.getFirstChild(); n != null; n = n.getNextSibling()) {
            if(n.getNodeType() == Node.TEXT_NODE) {
                // Skip text nodes. This should only be whitespace.
                continue;
            }

            // Make sure that this is a saml:AttributeValue node.
            if(!"saml:AttributeValue".equals(n.getNodeName())) {
                throw new RuntimeException("Child node of saml::Attribute wasn't saml::AttributeValue. Was: " + n.getNodeName());
            }

            // This saml:AttributeValue node should contain one text node with the value.
            Node textNode = n.getFirstChild();
            if(textNode == null) {
                throw new RuntimeException("No child node in saml::AttributeValue.");
            }
            if(textNode.getNodeType() != Node.TEXT_NODE) {
                throw new RuntimeException("Child node of saml::AttributeValue wasn't a text node. Was: " + n.getNodeType());
            }

            // Extract the string data from the text node.
            String data = ((Text)textNode).getData();

            // Add the text to the value list.
            values.add(data);
        }

        // Return the value list.
        return values;
    }

    /**
     * This is a helper function which parses a XML-document in a String into a
     * Document.
     * 
     * @param xml the string with the XML-document.
     * @return the Document which is created from the xml string.
     */
    private Document parseXML(String xml) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        try {
            Document document = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
            return document;
        } catch (SAXException e) {
            throw new RuntimeException("Malformed XML from the lasso library.", e);
        } catch (IOException e) {
            throw new RuntimeException("Got an IOException from a StringReader.", e);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("ParserConfigurationException while creating a DocumentBuilder", e);
        }
        
    }

    /**
     * Finds or creates an Attribute object for the given name.
     *
     * @param name name of the attribute
     * @return the attribute for the given name.
     */
    private Attribute findAttribute(String name) {
        Attribute attribute = this.attributes.get(name);
        if(attribute != null) {
            return attribute;
        }

        attribute = new Attribute(name);
        this.attributes.put(name, attribute);
        return attribute;
    }

    /**
     * This function extracts all the attributes from the given SAML2 response object. The
     * attributes are returned as a Map with the attribute name as the key and an Attribute-
     * object as the value.
     *
     * @param response the SAML2 response object.
     * @return a Map with the name of the attributes as key, and an Attribute object as value.
     */
    public static Map<String, Attribute> extractAttributes(Config config, Samlp2Response response)
            throws ServletException {

        AttributeExtractor p = new AttributeExtractor(config, response);

        return p.attributes;
    }
}
