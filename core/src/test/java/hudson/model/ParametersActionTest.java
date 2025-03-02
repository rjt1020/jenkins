package hudson.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.powermock.api.mockito.PowerMockito.mock;

import hudson.EnvVars;
import hudson.model.queue.SubTask;
import hudson.tasks.BuildWrapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.Issue;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
public class ParametersActionTest {

    private ParametersAction baseParamsAB;
    private StringParameterValue baseA;

    @Before
    public void setUp() {
        baseA = new StringParameterValue("a", "base-a");
        StringParameterValue baseB = new StringParameterValue("b", "base-b");
        baseParamsAB = new ParametersAction(baseA, baseB);
    }

    @Test
    public void mergeShouldOverrideParameters() {
        StringParameterValue overrideB = new StringParameterValue("b", "override-b");
        ParametersAction extraParams = new ParametersAction(overrideB);

        ParametersAction params = baseParamsAB.merge(extraParams);

        StringParameterValue a = (StringParameterValue) params.getParameter("a");
        StringParameterValue b = (StringParameterValue) params.getParameter("b");
        assertEquals(baseA, a);
        assertEquals(overrideB, b);
    }

    @Test
    public void mergeShouldCombineDisparateParameters() {
        StringParameterValue overrideB = new StringParameterValue("b", "override-b");
        ParametersAction extraParams = new ParametersAction(overrideB);

        ParametersAction params = baseParamsAB.merge(extraParams);

        StringParameterValue a = (StringParameterValue) params.getParameter("a");
        StringParameterValue b = (StringParameterValue) params.getParameter("b");
        assertEquals(baseA, a);
        assertEquals(overrideB, b);
    }

    @Test
    public void mergeShouldHandleEmptyOverrides() {
        ParametersAction params = baseParamsAB.merge(new ParametersAction());

        StringParameterValue a = (StringParameterValue) params.getParameter("a");
        assertEquals(baseA, a);
    }

    @Test
    public void mergeShouldHandleNullOverrides() {
        ParametersAction params = baseParamsAB.merge(null);

        StringParameterValue a = (StringParameterValue) params.getParameter("a");
        assertEquals(baseA, a);
    }

    @Test
    public void mergeShouldReturnNewInstanceWithOverride() {
        StringParameterValue overrideA = new StringParameterValue("a", "override-a");
        ParametersAction overrideParams = new ParametersAction(overrideA);

        ParametersAction params = baseParamsAB.merge(overrideParams);

        assertNotSame(baseParamsAB, params);
    }

    @Test
    public void createUpdatedShouldReturnNewInstanceWithNullOverride() {
        ParametersAction params = baseParamsAB.createUpdated(null);

        assertNotSame(baseParamsAB, params);
    }
    
    @Test
    @Issue("JENKINS-15094")
    public void checkNullParameterValues() {
        SubTask subtask = mock(SubTask.class);
        Build build = mock(Build.class);
                   
        // Prepare parameters Action
        StringParameterValue A = new StringParameterValue("A", "foo");
        StringParameterValue B = new StringParameterValue("B", "bar");
        ParametersAction parametersAction = new ParametersAction(A, null, B);
        ParametersAction parametersAction2 = new ParametersAction(A,null);
        
        // Non existent parameter
        assertNull(parametersAction.getParameter("C"));   
        assertNull(parametersAction.getAssignedLabel(subtask));
        
        // Interaction with build
        EnvVars vars = new EnvVars();
        parametersAction.buildEnvironment(build, vars);
        assertEquals(2, vars.size());   
        parametersAction.createVariableResolver(build);
        
        List<BuildWrapper> wrappers = new ArrayList<>();
        parametersAction.createBuildWrappers(build, wrappers);
        assertEquals(0, wrappers.size());
        
        // Merges and overrides
        assertEquals(3, parametersAction.createUpdated(parametersAction2.getParameters()).getParameters().size());
        assertEquals(3, parametersAction.merge(parametersAction2).getParameters().size());        
    }
}
