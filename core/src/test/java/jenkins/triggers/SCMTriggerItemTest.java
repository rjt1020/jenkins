package jenkins.triggers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import hudson.model.Item;
import hudson.model.TaskListener;
import jenkins.scm.SCMDecisionHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.Issue;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@SuppressWarnings("deprecation")
@RunWith(PowerMockRunner.class)
public class SCMTriggerItemTest {

    @Test
    @Issue("JENKINS-36232")
    @PrepareForTest(SCMDecisionHandler.class)
    public void noVetoDelegatesPollingToAnSCMedItem() {
        // given
        PowerMockito.mockStatic(SCMDecisionHandler.class);
        PowerMockito.when(SCMDecisionHandler.firstShouldPollVeto(any(Item.class))).thenReturn(null);
        hudson.model.SCMedItem scMedItem = Mockito.mock(hudson.model.SCMedItem.class);
        TaskListener listener = Mockito.mock(TaskListener.class);

        // when
        SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(scMedItem).poll(listener);

        // then
        verify(scMedItem).poll(listener);
    }

}
