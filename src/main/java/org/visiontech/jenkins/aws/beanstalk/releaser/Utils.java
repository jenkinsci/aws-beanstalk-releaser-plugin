package org.visiontech.jenkins.aws.beanstalk.releaser;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.ClientConfigurationFactory;
import hudson.ProxyConfiguration;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

public class Utils {

    public final static ClientConfigurationFactory factory = new ClientConfigurationFactory();

    public static ClientConfiguration getClientConfiguration() {
        ProxyConfiguration proxy = Jenkins.get().proxy;

        ClientConfiguration clientConfiguration = factory.getConfig();

        if (proxy != null) {
            clientConfiguration.setProxyHost(proxy.name);
            clientConfiguration.setProxyPort(proxy.port);
            clientConfiguration.setProxyUsername(proxy.getUserName());
            clientConfiguration.setProxyPassword(proxy.getPassword());
        }
        return clientConfiguration;
    }

    public static FormValidation doCheckValueIsNotBlank(String value) {
        if (StringUtils.isBlank(value)) {
            return FormValidation.error(Messages.AWSEBReleaserBuilder_DescriptorImpl_errors_missingValue());
        }
        return FormValidation.ok();
    }
    
    public final static ListBoxModel.Option EMPTY_OPTION = new ListBoxModel.Option(StringUtils.EMPTY);

}
