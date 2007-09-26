package no.feide.client.lasso;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

/**
 * This class handles loading of configuration for this service.
 */
class Config {
    /**
     * The path to the configuration file. This is relative to the context root.
     */
    private static final String configFile = "conf/LassoTestServlet.properties";

    /**
     * The properties file we have loaded.
     */
    private Properties configProperties;

    /**
     * The servlet context this configuration belongs in.
     */
    private ServletContext context;

    /**
     * The separator we use to separate multiple attributes in a single AttributeValue node.
     */
    private String attributeSeparator;

    /**
     * The absolute path to the meta data for the service provider. 
     */
    private String spMetadataFile;
    /**
     * The absolute path to the private key for the service provider. This field may be null.
     */
    private String spPrivateKeyFile;

    /**
     * The absolute path to the meta data for the IdP.
     */
    private String idpMetadataFile;
    /**
     * The absolute path to the public key for the IdP. This field may be null. In that case we expect
     * to find the public key in the meta data for the IdP.
     */
    private String idpPublicKeyFile;

    /**
     * Loads and validates the configuration for this servlet.
     * @param servletContext 
     *
     * @throws ServletException if the configuration is invalid.
     */
    Config(ServletContext servletContext) throws ServletException {
        this.context = servletContext;

        this.loadServletProperties();

        this.parseProperties();
    }

    /**
     * Loads LassoTestServlet.properties. This file is expected to be stored in a
     * directory on the current class path.
     *
     * @throws ServletException if we don't find LassoTestServlet.properties.
     */
    private void loadServletProperties() throws ServletException {
        this.configProperties = new Properties();

        InputStream configStream = this.context.getResourceAsStream("/" + Config.configFile);
        if(configStream == null) {
            throw new ServletException("Could not load configuration file: "
                    + this.context.getRealPath("/" + Config.configFile));
        }

        // Read the configuration file.
        try {
            this.configProperties.load(configStream);
        } catch (IOException e) {
            throw new ServletException("Unable to read configuration file LassoTestServlet.properties from classpath.");
        }
    }

    /**
     * Parses the properties into fields, and validates the data.
     *
     * @throws ServletException on invalid properties.
     */
    private void parseProperties() throws ServletException {
        this.attributeSeparator = this.getRequiredProperty("no.feide.test.attribute.separator");

        this.spMetadataFile = this.findRequiredFilePath(this.getRequiredProperty("no.feide.test.lasso.meta.sp"));
        this.spPrivateKeyFile = this.findFilePath(this.getProperty("no.feide.test.lasso.meta.sp.privkey"));

        this.idpMetadataFile = this.findRequiredFilePath(this.getRequiredProperty("no.feide.test.lasso.meta.idp"));
        this.idpPublicKeyFile= this.findFilePath(this.getProperty("no.feide.test.lasso.meta.idp.pubkey"));
    }

    /**
     * Retrieves a property from the configuration file.
     *
     * @param name name of the property to retrieve.
     * @return value of the given property, or null if no property with the given name exists in the configuration file.
     */
    public String getProperty(String name) {
        return this.configProperties.getProperty(name);
    }

    /**
     * Retrieves a property. Throws an exception if the property isn't found.
     * 
     * @param name name of the property.
     * @return value of the given property.
     * @throws ServletException if the property isn't found.
     */
    public String getRequiredProperty(String name) throws ServletException {
        String ret = this.getProperty(name);

        if(ret == null) {
            throw new ServletException("Could not get required property: " + name);
        }

        return ret;
    }

    /**
     * Attempts to find the absolute path of a given file. 
     *
     * @param path path to a file, relative to the context root.
     * @return the absolute path to the file, or null if path = null.
     * @throws ServletException if we are unable to resolve the given path to a file.
     */
    private String findFilePath(String inPath) throws ServletException {
        if(inPath == null) {
            return null;
        }

        String workPath = inPath;
        // Make sure that the path starts with "/".
        if(workPath.length() == 0 || !workPath.startsWith("/")) {
            workPath = "/" + workPath;
        }

        // Get the path relative to the context root.
        workPath = this.context.getRealPath(workPath);

        // Make sure that the 
        File f = new File(workPath);
        if(!f.isFile()) {
            throw new ServletException("Unable to resolve path to a file. Original path: \""
                    + inPath + "\" Resolved path: \"" + workPath + "\"");
        }

        return f.getAbsolutePath();
    }

    /**
     * Attempts to find the absolute path to the given file.
     *
     * @param path (relative) path to a file. 
     * @return absolute path to the given file.
     * @throws ServletException if path=null or we don't find a file at the given path.
     */
    private String findRequiredFilePath(String path) throws ServletException {
        if(path == null) {
            throw new ServletException("No path given.");
        }

        return this.findFilePath(path);
    }

    /**
     * Retrieves the attribute separator which is set in the configuration file.
     * 
     * @return the attribute separator.
     */
    public String getAttributeSeparator() {
        return this.attributeSeparator;
    }

    /**
     * Retrieves the absolute path to the meta data file for the SP.
     *
     * @return the absolute path to the meta data file for the SP.
     */
    public String getSPMetadataPath() {
        return this.spMetadataFile;
    }

    /**
     * Retrieves the absolute path to the private key for the SP. This may be null, in which
     * case the user hasn't set a private key to the SP.
     * 
     * @return the absolute path to the private key for the SP, or null if the user hasn't set a private key for the SP.
     */
    public String getSPPrivateKeyPath() {
        return this.spPrivateKeyFile;
    }

    /**
     * Retrieves the absolute path to the meta data file for the IdP.
     *
     * @return the absolute path to the meta data file for the IdP.
     */
    public String getIdPMetadataPath() {
        return this.idpMetadataFile;
    }

    /**
     * Retrieves the absolute path to the public key for the IdP. This may return null, in which
     * case the user hasn't specified a separate file for the public key. If this is the case, then
     * we expect to find the public key in the meta data.
     *
     * @return the absolute path to the public key for the IdP, or null if the user hasn't set a path to the public key.
     */
    public String getIdpPublicKeyPath() {
        return this.idpPublicKeyFile;
    }
}
