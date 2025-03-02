package hudson.cli;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.model.ViewTest.CompositeView;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import jenkins.model.Jenkins;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Jenkins.class)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
@SuppressStaticInitializationFor("hudson.cli.CLICommand")
public class ListJobsCommandTest {

    private /*final*/ Jenkins jenkins;
    private /*final*/ ListJobsCommand command;
    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    @Before
    public void setUp() {

        jenkins = mock(Jenkins.class);
        mockStatic(Jenkins.class);
        when(Jenkins.get()).thenReturn(jenkins);
        command = mock(ListJobsCommand.class, Mockito.CALLS_REAL_METHODS);
        command.stdout = new PrintStream(stdout);
        command.stderr = new PrintStream(stderr);
    }

    @Test
    public void failForNonexistentName() {

        when(jenkins.getView("NoSuchViewOrItemGroup")).thenReturn(null);
        when(jenkins.getItemByFullName("NoSuchViewOrItemGroup")).thenReturn(null);

        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> runWith("NoSuchViewOrItemGroup"));
        assertThat(e.getMessage(), containsString("No view or item group with the given name 'NoSuchViewOrItemGroup' found."));
        assertThat(stdout, is(empty()));
    }

    @Test
    public void getAllJobsForEmptyName() throws Exception {

        final List<TopLevelItem> jenkinsJobs = Arrays.asList(
                job("some-job"), job("some-other-job")
        );

        when(jenkins.getItems()).thenReturn(jenkinsJobs);

        assertThat(runWith(null), equalTo(0));
        assertThat(stderr, is(empty()));
        assertThat(stdout, listsJobs("some-job", "some-other-job"));
    }

    @Test
    public void getJobsFromView() throws Exception {

        final Collection<TopLevelItem> viewJobs = Arrays.asList(
                job("some-job"), job("some-other-job")
        );

        final View customView = view();
        when(customView.getItems()).thenReturn(viewJobs);

        when(jenkins.getView("CustomView")).thenReturn(customView);

        assertThat(runWith("CustomView"), equalTo(0));
        assertThat(stderr, is(empty()));
        assertThat(stdout, listsJobs("some-job", "some-other-job"));
    }

    @Test
    public void getJobsRecursivelyFromViewGroup() throws Exception {

        final CompositeView rootView = mock(CompositeView.class);
        when(rootView.getAllItems()).thenCallRealMethod();
        final View leftView = view();
        final View rightView = view();

        final TopLevelItem rootJob = job("rootJob");
        final TopLevelItem leftJob = job("leftJob");
        final TopLevelItem rightJob = job("rightJob");
        final TopLevelItem sharedJob = job("sharedJob");

        when(rootView.getViews()).thenReturn(Arrays.asList(leftView, rightView));
        when(rootView.getItems()).thenReturn(Arrays.asList(rootJob, sharedJob));
        when(leftView.getItems()).thenReturn(Arrays.asList(leftJob, sharedJob));
        when(rightView.getItems()).thenReturn(Collections.singletonList(rightJob));

        when(jenkins.getView("Root")).thenReturn(rootView);

        assertThat(runWith("Root"), equalTo(0));
        assertThat(stderr, is(empty()));
        assertThat(stdout, listsJobs("rootJob", "leftJob", "rightJob", "sharedJob"));
    }

    private View view() {

        final View view = mock(View.class);

        when(view.getAllItems()).thenCallRealMethod();

        return view;
    }

    private TopLevelItem job(final String name) {

        final TopLevelItem item = mock(TopLevelItem.class);

        when(item.getName()).thenReturn(name);
        when(item.getDisplayName()).thenReturn(name);

        return item;
    }

    private int runWith(final String name) throws Exception {

        command.name = name;

        return command.run();
    }

    private TypeSafeMatcher<ByteArrayOutputStream> empty() {

        return new TypeSafeMatcher<ByteArrayOutputStream>() {

            @Override
            protected boolean matchesSafely(ByteArrayOutputStream item) {

                return item.toString().isEmpty();
            }

            @Override
            public void describeTo(Description description) {

                description.appendText("Empty output");
            }
        };
    }

    private TypeSafeMatcher<ByteArrayOutputStream> listsJobs(final String... expected) {

        return new TypeSafeMatcher<ByteArrayOutputStream>() {

            @Override
            protected boolean matchesSafely(ByteArrayOutputStream item) {

                final HashSet<String> jobs = new HashSet<>(
                        Arrays.asList(item.toString().split(System.getProperty("line.separator")))
                );

                return new HashSet<>(Arrays.asList(expected)).equals(jobs);
            }

            @Override
            public void describeTo(Description description) {

                description.appendText("Job listing of " + Arrays.toString(expected));
            }
        };
    }
}
