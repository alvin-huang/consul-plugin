package com.inneractive.jenkins.plugins.consul.Steps;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import com.inneractive.jenkins.plugins.consul.*;
import com.inneractive.jenkins.plugins.consul.Util.ConsulUtil;
import hudson.*;
import hudson.model.*;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ConsulServiceDiscoveryStep extends Step{

    private String installationName;
    private String consulMasters;
    private String consulDatacenter;
    private String consulToken;
    private JSONObject overrideGlobalConsulConfigurations;
//    @Todo - fix installations in jelly file from util instead from a descriptor.
    private ConsulInstallation consulInstallation;
    private List<ConsulOperation> operationList;
    private ConsulGlobalConfigurations.DescriptorImpl globalConsulConfigurationsDescriptor;

    @DataBoundConstructor
    public ConsulServiceDiscoveryStep(String installationName) {
        this.installationName = installationName;
        operationList = Collections.emptyList();

        Jenkins jenkinsInstance= Jenkins.getInstance();
        if (jenkinsInstance != null)
            globalConsulConfigurationsDescriptor = ((ConsulGlobalConfigurations.DescriptorImpl)jenkinsInstance.getDescriptor(ConsulGlobalConfigurations.class));
    }

    @DataBoundSetter
    public void setOverrideGlobalConsulConfigurations(JSONObject overrideGlobalConsulConfigurations) {
        consulMasters = overrideGlobalConsulConfigurations.getString("consulMasters").replaceAll(" ", "");
        consulDatacenter = overrideGlobalConsulConfigurations.getString("consulDatacenter");
        consulToken = overrideGlobalConsulConfigurations.getString("consulToken");
        this.overrideGlobalConsulConfigurations = overrideGlobalConsulConfigurations;
    }

    @DataBoundSetter
    public void setOperationList(List<ConsulOperation> operationList) {
        this.operationList = operationList;
    }

    public String getInstallationName() {
        return installationName;
    }

    public JSONObject getOverrideGlobalConsulConfigurations() {
        return overrideGlobalConsulConfigurations;
    }

    public String getConsulMasters() {
        return consulMasters;
    }

    public String getConsulDatacenter() {
        return consulDatacenter;
    }

    public String getConsulToken() {
        return consulToken;
    }

    public ConsulInstallation getConsulInstallation() {
        return consulInstallation;
    }

    private String getMasters() {
        return globalConsulConfigurationsDescriptor.getConsulMasters(consulMasters);
    }

    private String getDatacenter() {
        return globalConsulConfigurationsDescriptor.getConsulDatacenter(consulDatacenter);
    }

    private String getToken() {
        return globalConsulConfigurationsDescriptor.getConsulToken(consulToken);
    }

    public List<ConsulOperation> getOperationList() {
        return operationList;
    }

    @Override
    public StepExecution start(StepContext stepContext) throws Exception {
        return new Execution(this, stepContext);
    }

    private static class Execution extends SynchronousStepExecution<JsonObject> {
        private Run run;
        private TaskListener taskListener;
        private FilePath filePath;
        private EnvVars envVars;
        private Launcher launcher;
        ConsulServiceDiscoveryStep step;
        Proc consulAgentProcess;

        protected Execution(final ConsulServiceDiscoveryStep step, final @Nonnull StepContext context) throws IOException, InterruptedException {
            super(context);
            run = context.get(Run.class);
            taskListener = context.get(TaskListener.class);
            filePath = context.get(FilePath.class);
            envVars = context.get(EnvVars.class);
            launcher = context.get(Launcher.class);
            this.step = step;
        }

        @Override
        protected JsonObject run() throws Exception {
            JsonObject response = new JsonObject();
            consulAgentProcess = ConsulUtil.joinConsul(run, launcher, taskListener, filePath, step.installationName, step.globalConsulConfigurationsDescriptor.getConsulDatacenter(step.getConsulDatacenter()), step.globalConsulConfigurationsDescriptor.getConsulMasters(step.getMasters()), step.globalConsulConfigurationsDescriptor.getConsulToken(step.getToken()));
            if ( consulAgentProcess != null) {
                for (ConsulOperation operation : step.operationList){
                    operation.perform(run, launcher, taskListener);
                    response.add(operation.getOperationName(), operation.getResponse());
                }
            } else{
                taskListener.getLogger().println("Couldn't connect to consul network.");
                return new JsonObject();
            }
            ConsulUtil.killConsulAgent(run, launcher, taskListener, filePath, step.installationName, consulAgentProcess);
            consulAgentProcess = null;
            // @Todo fix json response
            return response;
        }

    }

    @Extension(optional = true)
    public static class ConsulStepDescriptor extends StepDescriptor {

        public List<ConsulOperationDescriptor> getOperations() {
            return ConsulOperationDescriptor.all();
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, TaskListener.class, EnvVars.class, Launcher.class, FilePath.class);
        }

        @Override
        public String getFunctionName() {
            return "Consul";
        }

        @Override
        public String getDisplayName() {
            return "ConsulStep";
        }
    }
}
