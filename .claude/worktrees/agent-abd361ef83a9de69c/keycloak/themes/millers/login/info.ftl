<#--
  Millers HCM custom info.ftl (M42).

  Overrides the bundled `keycloak.v2` info page so screens like
  "You are already logged in", "Email sent", "Action confirmed", etc.
  always carry a styled "Back to Millers HCM" button.

  The default upstream template only renders a continue link when one
  of `pageRedirectUri`, `actionUri`, or `client.baseUrl` is populated.
  Our SPA client (`hcm-web`) historically had no `baseUrl`, so users
  landed on a dead-end info page with no way back. This override falls
  back to a hard-coded application URL so the screen is never a
  dead-end — and uses the same `.pf-c-button.pf-m-primary` class as
  the login form so it picks up the lavender button style from
  resources/css/login.css.

  Variables Keycloak provides on this page:
    message.summary     — the info text (e.g. "You are already logged in.")
    messageHeader       — optional override for the card title
    skipLink            — set when the upstream template wants us to
                          OMIT the continue link (e.g. mid-flow info)
    pageRedirectUri     — preferred continue target (post-action)
    actionUri           — secondary continue target
    client.baseUrl      — last-resort target from the client config
-->
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        ${messageHeader!message.summary?no_esc}
    <#elseif section = "form">
        <div id="kc-info-message">
            <p class="instruction">
                ${message.summary?no_esc}<#if requiredActions??><#list requiredActions>: <b><#items as reqActionItem>${kcSanitize(msg("requiredAction.${reqActionItem}"))?no_esc}<#sep>, </#items></#list></#if>
            </p>

            <#-- Always render a continue affordance — we deliberately ignore
                 `skipLink` (Keycloak sets it after logout, etc.) because the
                 SPA at the fallback URL will pick up whatever state is
                 appropriate (re-prompt for login if no token, or land on
                 /home if a session is still active). -->
            <#assign continueHref = "">
            <#if pageRedirectUri?has_content>
                <#assign continueHref = pageRedirectUri>
            <#elseif actionUri?has_content>
                <#assign continueHref = actionUri>
            <#elseif client?? && client.baseUrl?has_content>
                <#assign continueHref = client.baseUrl>
            <#else>
                <#-- Last-resort hard-coded fallback. Matches the SPA origin
                     advertised by KC_HOSTNAME_URL in docker-compose.yml.
                     Production should set the client baseUrl +
                     KC_HOSTNAME_URL so this branch is never taken. -->
                <#assign continueHref = "http://localhost:5180/">
            </#if>

            <div id="kc-form-buttons" class="${properties.kcFormGroupClass!}">
                <a class="pf-c-button pf-m-primary pf-m-block"
                   style="display:flex;align-items:center;justify-content:center;text-decoration:none;"
                   href="${continueHref}">
                    ${kcSanitize(msg("backToApplication"))?no_esc}
                </a>
            </div>
        </div>
    </#if>
</@layout.registrationLayout>
