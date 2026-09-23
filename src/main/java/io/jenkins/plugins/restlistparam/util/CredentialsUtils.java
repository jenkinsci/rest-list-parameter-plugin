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

import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.springframework.security.core.Authentication;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class CredentialsUtils {

  private static final Logger log = Logger.getLogger(CredentialsUtils.class.getName());

  private CredentialsUtils() {
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
        authenticationFor(context),
        context,
        StandardCredentials.class,
        Collections.emptyList(),
        CredentialsMatchers.anyOf(
          CredentialsMatchers.instanceOf(StringCredentials.class),
          CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class)))
      .includeCurrentValue(credentialsId);
  }

  public static ListBoxModel doFillStringCredentialsIdItems(final Item context,
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
        authenticationFor(context),
        context,
        StringCredentials.class,
        Collections.emptyList(),
        CredentialsMatchers.instanceOf(StringCredentials.class))
      .includeCurrentValue(credentialsId);
  }

  public static FormValidation doCheckCredentialsId(final Item context,
                                                    final String credentialsId)
  {
    if ((context == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER))
      || (context != null
      && !context.hasPermission(Item.EXTENDED_READ)
      && !context.hasPermission(CredentialsProvider.USE_ITEM)))
    {
      return FormValidation.ok();
    }

    if (credentialsId == null || credentialsId.isBlank()) {
      return FormValidation.ok();
    }
    if (credentialsId.startsWith("${") && credentialsId.endsWith("}")) {
      return FormValidation.warning(Messages.RLP_CredentialsUtils_ValidationWrn_ExpressionBased());
    }
    if (!findCredentials(context, credentialsId).isPresent()) {
      return FormValidation.error(Messages.RLP_CredentialsUtils_ValidationErr_CannotFind());
    }
    return FormValidation.ok();
  }

  public static Optional<StandardCredentials> findCredentials(final Item context,
                                                              final String credentialsId)
  {
    if (credentialsId == null || credentialsId.isBlank()) {
      return Optional.empty();
    }
    List<StandardCredentials> lookupCredentials = CredentialsProvider.lookupCredentialsInItem(
      StandardCredentials.class,
      context,
      authenticationFor(context),
      List.of());
    CredentialsMatcher allOf = CredentialsMatchers.allOf(
      CredentialsMatchers.withId(credentialsId),
      CredentialsMatchers.anyOf(
        CredentialsMatchers.instanceOf(StringCredentials.class),
        CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class)));
    return Optional.ofNullable(CredentialsMatchers.firstOrNull(lookupCredentials, allOf));
  }

  public static Optional<StringCredentials> findStringCredentials(final Item context,
                                                                  final String credentialsId)
  {
    if (credentialsId == null || credentialsId.isBlank()) {
      return Optional.empty();
    }
    List<StringCredentials> lookupCredentials = CredentialsProvider.lookupCredentialsInItem(
      StringCredentials.class,
      context,
      authenticationFor(context),
      List.of());
    CredentialsMatcher matcher = CredentialsMatchers.allOf(
      CredentialsMatchers.withId(credentialsId),
      CredentialsMatchers.instanceOf(StringCredentials.class));
    return Optional.ofNullable(CredentialsMatchers.firstOrNull(lookupCredentials, matcher));
  }

  private static Authentication authenticationFor(final Item context) {
    return context instanceof Queue.Task ? Tasks.getAuthenticationOf2((Queue.Task) context) : ACL.SYSTEM2;
  }
}
