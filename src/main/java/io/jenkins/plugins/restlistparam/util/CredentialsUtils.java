package io.jenkins.plugins.restlistparam.util;

import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.restlistparam.Messages;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class CredentialsUtils {

  private static final Logger log = Logger.getLogger(CredentialsUtils.class.getName());

  private CredentialsUtils()
  {
    throw new IllegalStateException("Utility class");
  }

  public static ListBoxModel doFillCredentialsIdItems(final Item context,
                                                      final String credentialsId)
  {
    if ((context == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER))
      || (context != null
      && !context.hasPermission(Item.EXTENDED_READ)
      && !context.hasPermission(CredentialsProvider.USE_ITEM)))
    {
      log.info(Messages.RLP_CredentialsUtils_info_NoPermission());
      return new StandardListBoxModel().includeCurrentValue(credentialsId);
    }
    return new StandardListBoxModel()
      .includeEmptyValue()
      .includeMatchingAs(
        context instanceof Queue.Task ? Tasks.getAuthenticationOf((Queue.Task) context) : ACL.SYSTEM,
        context,
        StandardCredentials.class,
        Collections.emptyList(),
        CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StringCredentials.class),
                                  CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class)))
      .includeCurrentValue(credentialsId);
  }

  public static FormValidation doCheckFillCredentialsId(final String credentialsId)
  {
    if (StringUtils.isBlank(credentialsId)) {
      return FormValidation.ok();
    }
    if (!findCredentials(credentialsId).isPresent()) {
      return FormValidation.error(Messages.RLP_CredentialsUtils_warn_CannotFind());
    }
    return FormValidation.ok();
  }

  public static Optional<StandardCredentials> findCredentials(final String credentialsId)
  {
    if (StringUtils.isBlank(credentialsId)) {
      return Optional.empty();
    }
    final List<StandardCredentials> lookupCredentials =
      CredentialsProvider.lookupCredentials(
        StandardCredentials.class,
        (Item) null,
        ACL.SYSTEM,
        Collections.emptyList());
    final CredentialsMatcher allOf = CredentialsMatchers.allOf(
      CredentialsMatchers.withId(credentialsId),
      CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StringCredentials.class),
                                CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class)));
    return Optional.ofNullable(CredentialsMatchers.firstOrNull(lookupCredentials, allOf));
  }
}
