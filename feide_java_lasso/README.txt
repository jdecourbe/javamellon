Feide Java Lasso servlet example
========================================================================

This is an example of how you may use the Lasso library
(http://lasso.entrouvert.org/) to authenticate a user against Feide from
a Java servlet.


Java files
========================================================================

This servlet consists of the following Java files found in
src/no/feide/client/lasso/:

Attribute.java
        Stores an attribute name and one or more values.

AttributeExtractor.java
        Extracts Feide attributes from a Lasso Samlp2Response object.

Base64.java
        A public domain Java implementation of a base64 encoder and
        decoder from: http://iharder.sourceforge.net/current/java/base64

Config.java
        Loads the configuration for this servlet. (Where to find meta
        data files, and other information like that).

Debug.java
        A dummy-class which stands in for a proper logging class.

TestServlet.java
        The servlet.


Servlet mappings
========================================================================

WEB-INF/web.xml contains servlet mappings for this servlet. The default
is to map /<webapp context path>/endpoint/* to this servlet.
<webapp context path> is usually feide_java_lasso. This servlet handles
the following paths relative to /<webapp context path>/endpoint/:
- login: Initializes login by redirecting to the IdP.
- assertion: Assertion consumer - receives assertions from the IdP after
  login. This consumer handles both the HTTP-POST and the HTTP-Artifact
  SAML2 bindings.
- logout: Logout consumer - receives logout requests from the IdP. This
  consumer only accepts the HTTP-Redirect SAML2 binding. 


Lasso Java bindings
========================================================================

In addition to installing the standard Lasso library, you also need to
install the Lasso Java bindings. If you are running Debian Etch, then
you may install the liblasso-java package from
http://deb.entrouvert.org/. This will install lasso.jar in
/usr/share/java/ and libjlasso.so in /usr/lib/jni/.

If you are compiling from source, then (by default) lasso.jar will be
created in /usr/local/share/java/ and libjlasso.so will be created in
/usr/local/lib/java/.

We include a version of lasso.jar with this release, but you may have to
replace it if you are running a different version of lasso than the one
this release is built on. The file you have to replace is either located
in WEB-INF/lib/ or lib/, depending on what version of this example you
downloaded.
 
You also need to configure the servlet container, so that this servlet
can find libjlasso.so. There are several ways to accomplish this:

- Setting LD_LIBRARY_PATH to include the directory containing
  libjlasso.so before starting the servlet container.
- Setting java.library.path Java system property to include the
  directory with libjlasso.so.
- Copying libjlasso.so to a directory Java will search for libraries.


Compiling
========================================================================

This project uses ant (http://ant.apache.org/) to build itself. If you
have ant installed, you should be able to cd into the source directory
and run ant. Ant will then compile the source and build a war-file. This
war-file will be stored in the dist-directory.


Installing
========================================================================

This servlet deploys like any other web application. The precise method
used to deply the servlet will vary between different servlet
containers. Note that the Lasso library is built to use version 1.6 of
Java. This means that your servlet container must run on version 1.6 of
Java.

You will need to edit the configuration after deploying. See the next
section for details about the configuration. You may have to reload the
servlet after updating the configuration.


Configuring the servlet
========================================================================

The main configuration of this servlet is set in the file
conf/LassoTestServlet.properties. All paths in this file are relative to
the context root. It contains the following properties:

- no.feide.test.lasso.meta.sp
  Path to the meta data for this SP. conf/example.org-spMeta.xml
  contains an example of meta data. More instructions on the meta data
  for the SP is provided in the next section.

- no.feide.test.lasso.meta.sp.privkey
  Path to the private key for this SP. This line can be left commented
  out - this example doesn't require the private key.

- no.feide.test.lasso.meta.idp
  Path to the meta data for the IdP. conf/sam.feide.no-spMeta.xml
  contains meta data for feide.

- no.feide.test.lasso.meta.idp.pubkey
  Path to the public key of the IdP. Can be removed if the meta data file
  for the IdP contains the public key. conf/sam.feide.no.pem contains the
  public key for sam.feide.no.

- no.feide.test.url.logout
  URL which the logout link should link to. Leave this at the default.

- no.feide.test.attribute.separator
  The character which separates the base64 encoded attributes. Leave
  this at the default.


Meta data for the SP
========================================================================

Look at conf/example.org-spMeta.xml for an example of the meta data for
this SP. You can use this file as a base for your own meta data. You
only need to update the entityID attribute and the Location attributes.


Testing it
========================================================================

After you have deployed and configured the test servlet, you should be
able to visit https://<your domain>/<context path>/endpoint/login to be
redirected to the login page of the IdP. The default context path is
feide_java_lasso. After you have logged on, you should be redirected back
to the test servlet, and get a web page which should list the attributes
it received from the IdP.

The logout link on the attribute-page should call an IdP-initiated
logout. Note that this example doesn't store any session information,
and therefore doesn't do anything other than redirecting back to the IdP
when it receives a logout request.
