package org.visiontech.jenkins.aws.beanstalk.releaser;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClientBuilder;
import com.amazonaws.services.elasticbeanstalk.model.AWSElasticBeanstalkException;
import com.amazonaws.services.elasticbeanstalk.model.DescribeApplicationVersionsRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeApplicationVersionsResult;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import com.amazonaws.services.elasticbeanstalk.model.UpdateEnvironmentRequest;
import com.amazonaws.services.elasticbeanstalk.model.UpdateEnvironmentResult;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.GetUserResult;
import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsHelper;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.util.FormValidation;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.ItemGroup;
import hudson.tasks.BuildStep;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;

public class AWSEBReleaserBuilder extends Builder implements BuildStep {

    private final String credentialId;
    private final String awsRegion;
    private final String applicationName;
    private final String environmentId;
    private final String versionLabel;

    @DataBoundConstructor
    public AWSEBReleaserBuilder(String credentialId, String awsRegion, String applicationName, String environmentId, String versionLabel) {
        this.credentialId = credentialId;
        this.awsRegion = awsRegion;
        this.applicationName = applicationName;
        this.environmentId = environmentId;
        this.versionLabel = versionLabel;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public String getAwsRegion() {
        return awsRegion;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getEnvironmentId() {
        return environmentId;
    }

    public String getVersionLabel() {
        return versionLabel;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

        EnvVars env = build.getEnvironment(listener);
        String expand = env.expand(versionLabel);

        AWSElasticBeanstalkClientBuilder builder = AWSElasticBeanstalkClientBuilder.standard();
        builder.withCredentials(CredentialsProvider.findCredentialById(credentialId, AmazonWebServicesCredentials.class, build, Collections.EMPTY_LIST));
        builder.withClientConfiguration(Utils.getClientConfiguration());
        builder.withRegion(Regions.fromName(awsRegion));

        AWSElasticBeanstalk beanstalk = builder.build();

        DescribeApplicationVersionsRequest describeApplicationVersionsRequest = new DescribeApplicationVersionsRequest();
        describeApplicationVersionsRequest.setApplicationName(applicationName);
        describeApplicationVersionsRequest.setVersionLabels(Arrays.asList(expand));

        DescribeApplicationVersionsResult describeApplicationVersions = beanstalk.describeApplicationVersions(describeApplicationVersionsRequest);

        if (CollectionUtils.isEmpty(describeApplicationVersions.getApplicationVersions())) {
            listener.getLogger().println(Messages.AWSEBReleaserBuilder_DescriptorImpl_errors_versionNotFound());
            return false;
        }

        UpdateEnvironmentRequest updateEnvironmentRequest = new UpdateEnvironmentRequest();

        updateEnvironmentRequest.setApplicationName(applicationName);
        updateEnvironmentRequest.setEnvironmentId(environmentId);
        updateEnvironmentRequest.setVersionLabel(expand);

        UpdateEnvironmentResult updateEnvironment = beanstalk.updateEnvironment(updateEnvironmentRequest);
                
        if (!Objects.equals(HttpURLConnection.HTTP_OK, updateEnvironment.getSdkHttpMetadata().getHttpStatusCode()) || !Objects.equals("Updating", updateEnvironment.getStatus()) || !Objects.equals(expand, updateEnvironment.getVersionLabel())){
            listener.getLogger().println(Messages.AWSEBReleaserBuilder_DescriptorImpl_errors_updateError());
            return false;
        }

        return true;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckCredentialId(@QueryParameter String value, @AncestorInPath ItemGroup context) throws IOException, ServletException {

            if (StringUtils.isBlank(value)) {
                return FormValidation.error(Messages.AWSEBReleaserBuilder_DescriptorImpl_errors_missingValue());
            }

            AmazonIdentityManagementClientBuilder builder = AmazonIdentityManagementClientBuilder.standard();
            builder.withCredentials(AWSCredentialsHelper.getCredentials(value, context));
            builder.withClientConfiguration(Utils.getClientConfiguration());

            AmazonIdentityManagement iam = builder.build();
            GetUserResult user = iam.getUser();

            if (Objects.isNull(user) || Objects.isNull(user.getSdkHttpMetadata()) || !Objects.equals(HttpURLConnection.HTTP_OK, user.getSdkHttpMetadata().getHttpStatusCode())) {
                return FormValidation.error(Messages.AWSEBReleaserBuilder_DescriptorImpl_errors_credentialNotFound());
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckAwsRegion(@QueryParameter String value) {
            return Utils.doCheckValueIsNotBlank(value);
        }

        public FormValidation doCheckApplicationName(@QueryParameter String value) {
            return Utils.doCheckValueIsNotBlank(value);
        }

        public FormValidation doCheckEnvironmentId(@QueryParameter String value) {
            return Utils.doCheckValueIsNotBlank(value);
        }

        public FormValidation doCheckVersionLabel(@QueryParameter String value) {
            return Utils.doCheckValueIsNotBlank(value);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.AWSEBReleaserBuilder_DescriptorImpl_DisplayName();
        }

        public ListBoxModel doFillCredentialIdItems(@AncestorInPath ItemGroup context) {
            return AWSCredentialsHelper.doFillCredentialsIdItems(context);
        }

        public ListBoxModel doFillAwsRegionItems() {
            return new ListBoxModel(ListUtils.union(Arrays.asList(Utils.EMPTY_OPTION), Arrays.asList(Regions.values()).stream().map(region -> new ListBoxModel.Option(region.getDescription(), region.getName())).collect(Collectors.toList())));
        }

        public ListBoxModel doFillApplicationNameItems(@AncestorInPath ItemGroup context, @QueryParameter String credentialId, @QueryParameter String awsRegion) {
            if (StringUtils.isBlank(credentialId) || StringUtils.isBlank(awsRegion)) {
                return new ListBoxModel(Utils.EMPTY_OPTION);
            }
            try {
                AWSElasticBeanstalkClientBuilder builder = AWSElasticBeanstalkClientBuilder.standard();
                builder.withCredentials(AWSCredentialsHelper.getCredentials(credentialId, context));
                builder.withClientConfiguration(Utils.getClientConfiguration());
                builder.withRegion(Regions.fromName(awsRegion));
                AWSElasticBeanstalk beanstalk = builder.build();
                return new ListBoxModel(ListUtils.union(Arrays.asList(Utils.EMPTY_OPTION), beanstalk.describeApplications().getApplications().stream().map(application -> new ListBoxModel.Option(application.getApplicationName())).collect(Collectors.toList())));
            } catch (AWSElasticBeanstalkException exception) {
                return new ListBoxModel();
            }
        }

        public ListBoxModel doFillEnvironmentIdItems(@AncestorInPath ItemGroup context, @QueryParameter String credentialId, @QueryParameter String awsRegion, @QueryParameter String applicationName) {
            if (StringUtils.isBlank(credentialId) || StringUtils.isBlank(awsRegion) || StringUtils.isBlank(applicationName)) {
                return new ListBoxModel(Utils.EMPTY_OPTION);
            }
            AWSElasticBeanstalkClientBuilder builder = AWSElasticBeanstalkClientBuilder.standard();
            builder.withCredentials(AWSCredentialsHelper.getCredentials(credentialId, context));
            builder.withClientConfiguration(Utils.getClientConfiguration());
            builder.withRegion(Regions.fromName(awsRegion));

            AWSElasticBeanstalk beanstalk = builder.build();

            DescribeEnvironmentsRequest describeEnvironmentsRequest = new DescribeEnvironmentsRequest();
            describeEnvironmentsRequest.setApplicationName(applicationName);
            return new ListBoxModel(ListUtils.union(Arrays.asList(Utils.EMPTY_OPTION), beanstalk.describeEnvironments(describeEnvironmentsRequest).getEnvironments().stream().map(environment -> new ListBoxModel.Option(environment.getEnvironmentName(), environment.getEnvironmentId())).collect(Collectors.toList())));
        }

    }

}
