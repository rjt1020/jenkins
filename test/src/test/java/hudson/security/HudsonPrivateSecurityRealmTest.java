/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc. and others
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.security;

import static hudson.security.HudsonPrivateSecurityRealm.PASSWORD_ENCODER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.util.Cookie;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.model.User;
import hudson.security.pages.SignupPage;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jenkins.security.ApiTokenProperty;
import jenkins.security.SecurityListener;
import jenkins.security.apitoken.ApiTokenTestHelper;
import jenkins.security.seed.UserSeedProperty;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.TestExtension;
import org.mindrot.jbcrypt.BCrypt;

@For({UserSeedProperty.class, HudsonPrivateSecurityRealm.class})
public class HudsonPrivateSecurityRealmTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private SpySecurityListenerImpl spySecurityListener;

    @Before
    public void linkExtension() {
        spySecurityListener = ExtensionList.lookup(SecurityListener.class).get(SpySecurityListenerImpl.class);
    }

    @Before
    public void setup() throws Exception {
        Field field = HudsonPrivateSecurityRealm.class.getDeclaredField("ID_REGEX");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Issue("SECURITY-243")
    @Test
    public void fullNameCollisionPassword() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        
        User u1 = securityRealm.createAccount("user1", "password1");
        u1.setFullName("User One");
        u1.save();

        User u2 = securityRealm.createAccount("user2", "password2");
        u2.setFullName("User Two");
        u2.save();

        WebClient wc1 = j.createWebClient();
        wc1.login("user1", "password1");

        WebClient wc2 = j.createWebClient();
        wc2.login("user2", "password2");

        
        // Check both users can use their token
        XmlPage w1 = (XmlPage) wc1.goTo("whoAmI/api/xml", "application/xml");
        assertThat(w1, hasXPath("//name", is("user1")));
        
        XmlPage w2 = (XmlPage) wc2.goTo("whoAmI/api/xml", "application/xml");
        assertThat(w2, hasXPath("//name", is("user2")));

        u1.setFullName("user2");
        u1.save();
        
        // check the tokens still work
        wc1 = j.createWebClient();
        wc1.login("user1", "password1");

        wc2 = j.createWebClient();
        // throws FailingHttpStatusCodeException on login failure
        wc2.login("user2", "password2");

        // belt and braces in case the failed login no longer throws exceptions.
        w1 = (XmlPage) wc1.goTo("whoAmI/api/xml", "application/xml");
        assertThat(w1, hasXPath("//name", is("user1")));
        
        w2 = (XmlPage) wc2.goTo("whoAmI/api/xml", "application/xml");
        assertThat(w2, hasXPath("//name", is("user2")));
    }

    @Issue("SECURITY-243")
    @Test
    public void fullNameCollisionToken() throws Exception {
        ApiTokenTestHelper.enableLegacyBehavior();
        
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        
        User u1 = securityRealm.createAccount("user1", "password1");
        u1.setFullName("User One");
        u1.save();
        String u1Token = u1.getProperty(ApiTokenProperty.class).getApiToken();

        User u2 = securityRealm.createAccount("user2", "password2");
        u2.setFullName("User Two");
        u2.save();
        String u2Token = u2.getProperty(ApiTokenProperty.class).getApiToken();

        WebClient wc1 = j.createWebClient();
        wc1.addRequestHeader("Authorization", basicHeader("user1", u1Token));
        //wc1.setCredentialsProvider(new FixedCredentialsProvider("user1", u1Token));

        WebClient wc2 = j.createWebClient();
        wc2.addRequestHeader("Authorization", basicHeader("user2", u2Token));
        //wc2.setCredentialsProvider(new FixedCredentialsProvider("user2", u1Token));
        
        // Check both users can use their token
        XmlPage w1 = (XmlPage) wc1.goTo("whoAmI/api/xml", "application/xml");
        assertThat(w1, hasXPath("//name", is("user1")));
        
        XmlPage w2 = (XmlPage) wc2.goTo("whoAmI/api/xml", "application/xml");
        assertThat(w2, hasXPath("//name", is("user2")));


        u1.setFullName("user2");
        u1.save();
        // check the tokens still work
        w1 = (XmlPage) wc1.goTo("whoAmI/api/xml", "application/xml");
        assertThat(w1, hasXPath("//name", is("user1")));
        
        w2 = (XmlPage) wc2.goTo("whoAmI/api/xml", "application/xml");
        assertThat(w2, hasXPath("//name", is("user2")));
    }


    private static String basicHeader(String user, String pass) {
        String str = user +':' + pass;
        String auth = java.util.Base64.getEncoder().encodeToString(str.getBytes(StandardCharsets.UTF_8));
        return "Basic " + auth;
    }

    @Test
    public void signup() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        JenkinsRule.WebClient wc = j.createWebClient();
        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername("alice");
        signup.enterPassword("alice");
        signup.enterFullName("Alice User");
        signup.enterEmail("alice@nowhere.com");
        HtmlPage success = signup.submit(j);
        assertThat(success.getElementById("main-panel").getTextContent(), containsString("Success"));
        assertThat(success.getAnchorByHref("/jenkins/user/alice").getTextContent(), containsString("Alice User"));


        assertEquals("Alice User", securityRealm.getUser("alice").getDisplayName());

    }

    @Issue("SECURITY-166")
    @Test
    public void anonymousCantSignup() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        JenkinsRule.WebClient wc = j.createWebClient();
        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername("anonymous");
        signup.enterFullName("Bob");
        signup.enterPassword("nothing");
        signup.enterEmail("noone@nowhere.com");
        signup = new SignupPage(signup.submit(j));
        signup.assertErrorContains("prohibited as a username");
        assertNull(User.get("anonymous", false, Collections.emptyMap()));
    }

    @Issue("SECURITY-166")
    @Test
    public void systemCantSignup() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        JenkinsRule.WebClient wc = j.createWebClient();
        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername("system");
        signup.enterFullName("Bob");
        signup.enterPassword("nothing");
        signup.enterEmail("noone@nowhere.com");
        signup = new SignupPage(signup.submit(j));
        signup.assertErrorContains("prohibited as a username");
        assertNull(User.get("system",false, Collections.emptyMap()));
    }

    /**
     * We don't allow prohibited fullnames since this may encumber auditing.
     */
    @Issue("SECURITY-166")
    @Test
    public void fullNameOfUnknownCantSignup() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        JenkinsRule.WebClient wc = j.createWebClient();
        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername("unknown2");
        signup.enterPassword("unknown2");
        signup.enterFullName("unknown");
        signup.enterEmail("noone@nowhere.com");
        signup = new SignupPage(signup.submit(j));
        signup.assertErrorContains("prohibited as a full name");
        assertNull(User.get("unknown2",false, Collections.emptyMap()));
    }

    @Issue("JENKINS-48383")
    @Test
    public void selfRegistrationTriggerLoggedIn() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        j.jenkins.setCrumbIssuer(null);

        assertTrue(spySecurityListener.loggedInUsernames.isEmpty());

        createFirstAccount("admin");
        assertEquals("admin", spySecurityListener.loggedInUsernames.get(0));

        createAccountByAdmin("alice");
        // no new event in such case
        assertTrue(spySecurityListener.loggedInUsernames.isEmpty());

        selfRegistration("bob");
        assertEquals("bob", spySecurityListener.loggedInUsernames.get(0));
    }

    @Issue("JENKINS-55307")
    @Test
    public void selfRegistrationTriggerUserCreation() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        j.jenkins.setCrumbIssuer(null);

        spySecurityListener.createdUsers.clear();
        assertTrue(spySecurityListener.createdUsers.isEmpty());

        selfRegistration("bob");
        selfRegistration("charlie");
        assertEquals("bob", spySecurityListener.createdUsers.get(0));
        assertEquals("charlie", spySecurityListener.createdUsers.get(1));
    }

    @Issue("JENKINS-55307")
    @Test
    public void userCreationFromRealm() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(securityRealm);

        spySecurityListener.createdUsers.clear();
        assertTrue(spySecurityListener.createdUsers.isEmpty());

        User u1 = securityRealm.createAccount("alice", "alicePassword");
        u1.setFullName("Alice User");
        u1.save();

        User u2 = securityRealm.createAccount("debbie", "debbiePassword");
        u2.setFullName("Debbie User");
        u2.save();

        assertEquals("alice", spySecurityListener.createdUsers.get(0));
        assertEquals("debbie", spySecurityListener.createdUsers.get(1));
    }

    @Issue("JENKINS-55307")
    @Test
    public void userCreationWithHashedPasswords() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(securityRealm);

        spySecurityListener.createdUsers.clear();
        assertTrue(spySecurityListener.createdUsers.isEmpty());

        securityRealm.createAccountWithHashedPassword("charlie_hashed", "#jbcrypt:" + BCrypt.hashpw("charliePassword", BCrypt.gensalt()));

        assertEquals("charlie_hashed", spySecurityListener.createdUsers.get(0));
    }

    private void createFirstAccount(String login) throws Exception {
        assertNull(User.getById(login, false));

        JenkinsRule.WebClient wc = j.createWebClient();

        HudsonPrivateSecurityRealm.SignupInfo info = new HudsonPrivateSecurityRealm.SignupInfo();
        info.username = login;
        info.password1 = login;
        info.password2 = login;
        info.fullname = StringUtils.capitalize(login);

        WebRequest request = new WebRequest(new URL(wc.getContextPath() + "securityRealm/createFirstAccount"), HttpMethod.POST);
        request.setRequestParameters(Arrays.asList(
                new NameValuePair("username", login),
                new NameValuePair("password1", login),
                new NameValuePair("password2", login),
                new NameValuePair("fullname", StringUtils.capitalize(login)),
                new NameValuePair("email", login + "@" + login + ".com")
        ));

        HtmlPage p = wc.getPage(request);
        assertEquals(200, p.getWebResponse().getStatusCode());
        assertTrue(p.getDocumentElement().getElementsByAttribute("div", "class", "error").isEmpty());

        assertNotNull(User.getById(login, false));
    }

    private void createAccountByAdmin(String login) throws Exception {
        // user should not exist before
        assertNull(User.getById(login, false));

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("admin");

        spySecurityListener.loggedInUsernames.clear();

        HtmlPage page = wc.goTo("securityRealm/addUser");
        HtmlForm form = page.getForms().stream()
                .filter(htmlForm -> htmlForm.getActionAttribute().endsWith("/securityRealm/createAccountByAdmin"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Form must be present"));

        form.getInputByName("username").setValueAttribute(login);
        form.getInputByName("password1").setValueAttribute(login);
        form.getInputByName("password2").setValueAttribute(login);
        form.getInputByName("fullname").setValueAttribute(StringUtils.capitalize(login));
        form.getInputByName("email").setValueAttribute(login + "@" + login + ".com");

        HtmlPage p = j.submit(form);
        assertEquals(200, p.getWebResponse().getStatusCode());
        assertTrue(p.getDocumentElement().getElementsByAttribute("div", "class", "error").isEmpty());

        assertNotNull(User.getById(login, false));
    }

    private void selfRegistration(String login) throws Exception {
        // user should not exist before
        assertNull(User.getById(login, false));

        JenkinsRule.WebClient wc = j.createWebClient();
        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername(login);
        signup.enterPassword(login);
        signup.enterFullName(StringUtils.capitalize(login));
        signup.enterEmail(login + "@" + login + ".com");

        HtmlPage p = signup.submit(j);
        assertEquals(200, p.getWebResponse().getStatusCode());
        assertTrue(p.getDocumentElement().getElementsByAttribute("div", "class", "error").isEmpty());

        assertNotNull(User.getById(login, false));
    }

    @TestExtension
    public static class SpySecurityListenerImpl extends SecurityListener {
        private final List<String> loggedInUsernames = new ArrayList<>();
        private final List<String> createdUsers = new ArrayList<>();

        @Override
        protected void loggedIn(@NonNull String username) {
            loggedInUsernames.add(username);
        }

        @Override
        protected void userCreated(@NonNull String username) { createdUsers.add(username); }
    }

    @Issue("SECURITY-786")
    @Test
    public void controlCharacterAreNoMoreValid() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        
        String password = "testPwd";
        String email = "test@test.com";
        int i = 0;
        
        // regular case = only accepting a-zA-Z0-9 + "-_"
        checkUserCanBeCreatedWith(securityRealm, "test" + i, password, "Test" + i, email);
        assertNotNull(User.getById("test" + i, false));
        i++;
        checkUserCanBeCreatedWith(securityRealm, "te-st_123" + i, password, "Test" + i, email);
        assertNotNull(User.getById("te-st_123" + i, false));
        i++;
        {// user id that contains invalid characters
            checkUserCannotBeCreatedWith(securityRealm, "test " + i, password, "Test" + i, email);
            i++;
            checkUserCannotBeCreatedWith(securityRealm, "te@st" + i, password, "Test" + i, email);
            i++;
            checkUserCannotBeCreatedWith(securityRealm, "test.com" + i, password, "Test" + i, email);
            i++;
            checkUserCannotBeCreatedWith(securityRealm, "test,com" + i, password, "Test" + i, email);
            i++;
            checkUserCannotBeCreatedWith(securityRealm, "test,com" + i, password, "Test" + i, email);
            i++;
            checkUserCannotBeCreatedWith(securityRealm, "testécom" + i, password, "Test" + i, email);
            i++;
            checkUserCannotBeCreatedWith(securityRealm, "Stargåte" + i, password, "Test" + i, email);
            i++;
            checkUserCannotBeCreatedWith(securityRealm, "te\u0000st" + i, password, "Test" + i, email);
        }
    }
    
    @Issue("SECURITY-786")
    @Test
    public void controlCharacterAreNoMoreValid_CustomRegex() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        
        String currentRegex = "^[A-Z]+[0-9]*$";
        
        Field field = HudsonPrivateSecurityRealm.class.getDeclaredField("ID_REGEX");
        field.setAccessible(true);
        field.set(null, currentRegex);
        
        String password = "testPwd";
        String email = "test@test.com";
        int i = 0;
        
        // regular case = only accepting a-zA-Z0-9 + "-_"
        checkUserCanBeCreatedWith(securityRealm, "TEST" + i, password, "Test" + i, email);
        assertNotNull(User.getById("TEST" + i, false));
        i++;
        checkUserCanBeCreatedWith(securityRealm, "TEST123" + i, password, "Test" + i, email);
        assertNotNull(User.getById("TEST123" + i, false));
        i++;
        {// user id that do not follow custom regex
            checkUserCannotBeCreatedWith_custom(securityRealm, "test " + i, password, "Test" + i, email, currentRegex);
            i++;
            checkUserCannotBeCreatedWith_custom(securityRealm, "@" + i, password, "Test" + i, email, currentRegex);
            i++;
            checkUserCannotBeCreatedWith_custom(securityRealm, "T2A" + i, password, "Test" + i, email, currentRegex);
            i++;
        }
        { // we can even change regex on the fly
            currentRegex = "^[0-9]*$";
            field.set(null, currentRegex);
    
            checkUserCanBeCreatedWith(securityRealm, "125213" + i, password, "Test" + i, email);
            assertNotNull(User.getById("125213" + i, false));
            i++;
            checkUserCannotBeCreatedWith_custom(securityRealm, "TEST12" + i, password, "Test" + i, email, currentRegex);
        }
    }

    @Test
    public void createAccountSupportsHashedPasswords() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(securityRealm);

        securityRealm.createAccountWithHashedPassword("user_hashed", "#jbcrypt:" + BCrypt.hashpw("password", BCrypt.gensalt()));

        WebClient wc = j.createWebClient();
        wc.login("user_hashed", "password");


        XmlPage w2 = (XmlPage) wc.goTo("whoAmI/api/xml", "application/xml");
        assertThat(w2, hasXPath("//name", is("user_hashed")));
    }

    @Test
    public void createAccountWithHashedPasswordRequiresPrefix() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        assertThrows(IllegalArgumentException.class, () -> securityRealm.createAccountWithHashedPassword("user_hashed", BCrypt.hashpw("password", BCrypt.gensalt())));
    }

    @Test
    public void hashedPasswordTest() {
        assertTrue("password is hashed", PASSWORD_ENCODER.isPasswordHashed("#jbcrypt:" + BCrypt.hashpw("password", BCrypt.gensalt())));
        assertFalse("password is not hashed", PASSWORD_ENCODER.isPasswordHashed("password"));
        assertFalse("only valid hashed passwords allowed", PASSWORD_ENCODER.isPasswordHashed("#jbcrypt:$2a$blah"));
        assertFalse("only valid hashed passwords allowed", PASSWORD_ENCODER.isPasswordHashed("#jbcrypt:password"));

        // real examples
        // password = a
        assertTrue(PASSWORD_ENCODER.isPasswordHashed("#jbcrypt:$2a$06$m0CrhHm10qJ3lXRY.5zDGO3rS2KdeeWLuGmsfGlMfOxih58VYVfxe"));
        // password = a
        assertTrue(PASSWORD_ENCODER.isPasswordHashed("#jbcrypt:$2a$12$8NJH3LsPrANStV6XtBakCez0cKHXVxmvxIlcz785vxAIZrihHZpeS"));

        // password = password
        assertFalse("too big number of iterations", PASSWORD_ENCODER.isPasswordHashed("#jbcrypt:$2a208$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));

        // until https://github.com/jeremyh/jBCrypt/pull/16 is merged, the lib released and the dep updated, only the version 2a is supported
        assertFalse("unsupported version", PASSWORD_ENCODER.isPasswordHashed("#jbcrypt:$2x$08$Ro0CUfOqk6cXEKf3dyaM7OhSCvnwM9s4wIX9JeLapehKK5YdLxKcm"));
        assertFalse("unsupported version", PASSWORD_ENCODER.isPasswordHashed("#jbcrypt:$2y$06$m0CrhHm10qJ3lXRY.5zDGO3rS2KdeeWLuGmsfGlMfOxih58VYVfxe"));

        assertFalse("invalid version", PASSWORD_ENCODER.isPasswordHashed("#jbcrypt:$2t$10$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        assertFalse("invalid version", PASSWORD_ENCODER.isPasswordHashed("#jbcrypt:$3t$10$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
    }

    @Test
    public void ensureHashingVersion_2a_isSupported() {
        assertTrue("version 2a is supported", BCrypt.checkpw("a", "$2a$06$m0CrhHm10qJ3lXRY.5zDGO3rS2KdeeWLuGmsfGlMfOxih58VYVfxe"));
    }

    @Test
    public void ensureHashingVersion_2x_isNotSupported() {
        assertThrows(IllegalArgumentException.class, () -> BCrypt.checkpw("abc", "$2x$08$Ro0CUfOqk6cXEKf3dyaM7OhSCvnwM9s4wIX9JeLapehKK5YdLxKcm"));
    }

    @Test
    public void ensureHashingVersion_2y_isNotSupported() {
        assertThrows(IllegalArgumentException.class, () -> BCrypt.checkpw("a", "$2y$08$cfcvVd2aQ8CMvoMpP2EBfeodLEkkFJ9umNEfPD18.hUF62qqlC/V."));
    }
    
    private void checkUserCanBeCreatedWith(HudsonPrivateSecurityRealm securityRealm, String id, String password, String fullName, String email) throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername(id);
        signup.enterPassword(password);
        signup.enterFullName(fullName);
        signup.enterEmail(email);
        HtmlPage success = signup.submit(j);
        assertThat(success.getElementById("main-panel").getTextContent(), containsString("Success"));
    }
    
    private void checkUserCannotBeCreatedWith(HudsonPrivateSecurityRealm securityRealm, String id, String password, String fullName, String email) throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername(id);
        signup.enterPassword(password);
        signup.enterFullName(fullName);
        signup.enterEmail(email);
        HtmlPage success = signup.submit(j);
        assertThat(success.getElementById("main-panel").getTextContent(), not(containsString("Success")));
        assertThat(success.getElementById("main-panel").getTextContent(), containsString(Messages.HudsonPrivateSecurityRealm_CreateAccount_UserNameInvalidCharacters()));
    }
    
    private void checkUserCannotBeCreatedWith_custom(HudsonPrivateSecurityRealm securityRealm, String id, String password, String fullName, String email, String regex) throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername(id);
        signup.enterPassword(password);
        signup.enterFullName(fullName);
        signup.enterEmail(email);
        HtmlPage success = signup.submit(j);
        assertThat(success.getElementById("main-panel").getTextContent(), not(containsString("Success")));
        assertThat(success.getElementById("main-panel").getTextContent(), containsString(regex));
    }

    @Test
    @Issue("SECURITY-1158")
    public void singupNoLongerVulnerableToSessionFixation() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        JenkinsRule.WebClient wc = j.createWebClient();

        // to trigger the creation of a session
        wc.goTo("");
        Cookie sessionBefore = wc.getCookieManager().getCookie("JSESSIONID");
        String sessionIdBefore = sessionBefore.getValue();

        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername("alice");
        signup.enterPassword("alice");
        signup.enterFullName("Alice User");
        signup.enterEmail("alice@nowhere.com");
        HtmlPage success = signup.submit(j);
        assertThat(success.getElementById("main-panel").getTextContent(), containsString("Success"));
        assertThat(success.getAnchorByHref("/jenkins/user/alice").getTextContent(), containsString("Alice User"));

        assertEquals("Alice User", securityRealm.getUser("alice").getDisplayName());

        Cookie sessionAfter = wc.getCookieManager().getCookie("JSESSIONID");
        String sessionIdAfter = sessionAfter.getValue();

        assertNotEquals(sessionIdAfter, sessionIdBefore);
    }

    @Test
    @Issue("SECURITY-1245")
    public void changingPassword_mustInvalidateAllSessions() throws Exception {
        User alice = prepareRealmAndAlice();
        String initialSeed = alice.getProperty(UserSeedProperty.class).getSeed();

        WebClient wc = j.createWebClient();
        WebClient wc_anotherTab = j.createWebClient();

        wc.login(alice.getId());
        assertUserConnected(wc, alice.getId());

        wc_anotherTab.login(alice.getId());
        assertUserConnected(wc_anotherTab, alice.getId());

        HtmlPage configurePage = wc.goTo(alice.getUrl() + "/configure");
        HtmlPasswordInput password1 = configurePage.getElementByName("user.password");
        HtmlPasswordInput password2 = configurePage.getElementByName("user.password2");

        password1.setText("alice2");
        password2.setText("alice2");

        HtmlForm form = configurePage.getFormByName("config");
        j.submit(form);

        assertUserNotConnected(wc, alice.getId());
        assertUserNotConnected(wc_anotherTab, alice.getId());

        String seedAfter = alice.getProperty(UserSeedProperty.class).getSeed();
        assertThat(seedAfter, not(is(initialSeed)));
    }

    @Test
    @Issue("SECURITY-1245")
    public void notChangingPassword_hasNoImpactOnSeed() throws Exception {
        User alice = prepareRealmAndAlice();
        String initialSeed = alice.getProperty(UserSeedProperty.class).getSeed();

        WebClient wc = j.createWebClient();
        WebClient wc_anotherTab = j.createWebClient();

        wc.login(alice.getId());
        assertUserConnected(wc, alice.getId());

        wc_anotherTab.login(alice.getId());
        assertUserConnected(wc_anotherTab, alice.getId());

        HtmlPage configurePage = wc.goTo(alice.getUrl() + "/configure");
        // not changing password this time
        HtmlForm form = configurePage.getFormByName("config");
        j.submit(form);

        assertUserConnected(wc, alice.getId());
        assertUserConnected(wc_anotherTab, alice.getId());

        String seedAfter = alice.getProperty(UserSeedProperty.class).getSeed();
        assertThat(seedAfter, is(initialSeed));
    }

    @Test
    @Issue("SECURITY-1245")
    public void changingPassword_withSeedDisable_hasNoImpact() throws Exception {
        boolean previousConfig = UserSeedProperty.DISABLE_USER_SEED;
        try {
            UserSeedProperty.DISABLE_USER_SEED = true;

            User alice = prepareRealmAndAlice();

            WebClient wc = j.createWebClient();
            WebClient wc_anotherTab = j.createWebClient();

            wc.login(alice.getId());
            assertUserConnected(wc, alice.getId());

            wc_anotherTab.login(alice.getId());
            assertUserConnected(wc_anotherTab, alice.getId());

            HtmlPage configurePage = wc.goTo(alice.getUrl() + "/configure");
            HtmlPasswordInput password1 = configurePage.getElementByName("user.password");
            HtmlPasswordInput password2 = configurePage.getElementByName("user.password2");

            password1.setText("alice2");
            password2.setText("alice2");

            HtmlForm form = configurePage.getFormByName("config");
            j.submit(form);

            assertUserConnected(wc, alice.getId());
            assertUserConnected(wc_anotherTab, alice.getId());
        } finally {
            UserSeedProperty.DISABLE_USER_SEED = previousConfig;
        }
    }

    private User prepareRealmAndAlice() throws Exception {
        j.jenkins.setDisableRememberMe(false);
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(securityRealm);

        return securityRealm.createAccount("alice", "alice");
    }

    private void assertUserConnected(JenkinsRule.WebClient wc, String expectedUsername) throws Exception {
        XmlPage page = (XmlPage) wc.goTo("whoAmI/api/xml", "application/xml");
        assertThat(page, hasXPath("//name", is(expectedUsername)));
    }

    private void assertUserNotConnected(JenkinsRule.WebClient wc, String notExpectedUsername) throws Exception {
        XmlPage page = (XmlPage) wc.goTo("whoAmI/api/xml", "application/xml");
        assertThat(page, hasXPath("//name", not(is(notExpectedUsername))));
    }
}
