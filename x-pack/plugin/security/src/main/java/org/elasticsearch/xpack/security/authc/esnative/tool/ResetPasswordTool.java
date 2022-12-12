/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.security.authc.esnative.tool;

import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.OptionSpecBuilder;

import org.elasticsearch.cli.ExitCodes;
import org.elasticsearch.cli.Terminal;
import org.elasticsearch.cli.UserException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.KeyStoreWrapper;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.core.CheckedFunction;
import org.elasticsearch.env.Environment;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.json.JsonXContent;
import org.elasticsearch.xpack.core.security.CommandLineHttpClient;
import org.elasticsearch.xpack.core.security.HttpResponse;
import org.elasticsearch.xpack.core.security.support.Validation;
import org.elasticsearch.xpack.security.tool.BaseRunAsSuperuserCommand;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.function.Function;

import static org.elasticsearch.xpack.core.security.CommandLineHttpClient.createURL;
import static org.elasticsearch.xpack.security.tool.CommandUtils.generatePassword;

class ResetPasswordTool extends BaseRunAsSuperuserCommand {

    private final Function<Environment, CommandLineHttpClient> clientFunction;
    private final OptionSpecBuilder interactive;
    private final OptionSpecBuilder auto;
    private final OptionSpecBuilder batch;
    private final OptionSpec<String> usernameOption;

    ResetPasswordTool() {
        this(CommandLineHttpClient::new, environment -> KeyStoreWrapper.load(environment.configFile()));
    }

    protected ResetPasswordTool(
        Function<Environment, CommandLineHttpClient> clientFunction,
        CheckedFunction<Environment, KeyStoreWrapper, Exception> keyStoreFunction
    ) {
        super(clientFunction, keyStoreFunction, "Resets the password of users in the native realm and built-in users.");
        interactive = parser.acceptsAll(List.of("i", "interactive"));
        auto = parser.acceptsAll(List.of("a", "auto")); // default
        batch = parser.acceptsAll(List.of("b", "batch"));
        usernameOption = parser.acceptsAll(List.of("u", "username"), "The username of the user whose password will be reset")
            .withRequiredArg()
            .required();
        this.clientFunction = clientFunction;
    }

    @Override
    protected void executeCommand(Terminal terminal, OptionSet options, Environment env, String username, SecureString password)
        throws Exception {
        final SecureString builtinUserPassword;
        final String providedUsername = options.valueOf(usernameOption);
        if (options.has(interactive)) {
            if (options.has(batch) == false) {
                terminal.println("This tool will reset the password of the [" + providedUsername + "] user.");
                terminal.println("You will be prompted to enter the password.");
                boolean shouldContinue = terminal.promptYesNo("Please confirm that you would like to continue", false);
                terminal.println("\n");
                if (shouldContinue == false) {
                    throw new UserException(ExitCodes.OK, "User cancelled operation");
                }
            }
            builtinUserPassword = promptForPassword(terminal, providedUsername);
        } else {
            if (options.has(batch) == false) {
                terminal.println("This tool will reset the password of the [" + providedUsername + "] user to an autogenerated value.");
                terminal.println("The password will be printed in the console.");
                boolean shouldContinue = terminal.promptYesNo("Please confirm that you would like to continue", false);
                terminal.println("\n");
                if (shouldContinue == false) {
                    throw new UserException(ExitCodes.OK, "User cancelled operation");
                }
            }
            builtinUserPassword = new SecureString(generatePassword(20));
        }
        try {
            final CommandLineHttpClient client = clientFunction.apply(env);
            final URL baseUrl = options.has(urlOption) ? new URL(options.valueOf(urlOption)) : new URL(client.getDefaultURL());
            final URL changePasswordUrl = createURL(baseUrl, "_security/user/" + providedUsername + "/_password", "?pretty");
            final HttpResponse httpResponse = client.execute(
                "POST",
                changePasswordUrl,
                username,
                password,
                () -> requestBodySupplier(builtinUserPassword),
                CommandLineHttpClient::responseBuilder
            );
            final int responseStatus = httpResponse.getHttpStatus();
            if (httpResponse.getHttpStatus() != HttpURLConnection.HTTP_OK) {
                final String cause = CommandLineHttpClient.getErrorCause(httpResponse);
                String message = "Failed to reset password for the ["
                    + providedUsername
                    + "] user. Unexpected http status ["
                    + responseStatus
                    + "].";
                if (null != cause) {
                    message += " Cause was " + cause;
                }
                throw new UserException(ExitCodes.TEMP_FAILURE, message);
            } else {
                if (options.has(interactive)) {
                    terminal.println("Password for the [" + providedUsername + "] user successfully reset.");
                } else {
                    terminal.println("Password for the [" + providedUsername + "] user successfully reset.");
                    terminal.print(Terminal.Verbosity.NORMAL, "New value: ");
                    terminal.println(Terminal.Verbosity.SILENT, builtinUserPassword.toString());
                }
            }
        } catch (Exception e) {
            throw new UserException(ExitCodes.TEMP_FAILURE, "Failed to reset password for the [" + providedUsername + "] user", e);
        } finally {
            builtinUserPassword.close();
        }
    }

    private SecureString promptForPassword(Terminal terminal, String providedUsername) {
        while (true) {
            SecureString password1 = new SecureString(terminal.readSecret("Enter password for [" + providedUsername + "]: "));
            Validation.Error err = Validation.Users.validatePassword(password1);
            if (err != null) {
                terminal.errorPrintln(err.toString());
                terminal.errorPrintln("Try again.");
                password1.close();
                continue;
            }
            try (SecureString password2 = new SecureString(terminal.readSecret("Re-enter password for [" + providedUsername + "]: "))) {
                if (password1.equals(password2) == false) {
                    terminal.errorPrintln("Passwords do not match.");
                    terminal.errorPrintln("Try again.");
                    password1.close();
                    continue;
                }
            }
            return password1;
        }
    }

    private String requestBodySupplier(SecureString pwd) throws Exception {
        XContentBuilder xContentBuilder = JsonXContent.contentBuilder();
        xContentBuilder.startObject().field("password", pwd.toString()).endObject();
        return Strings.toString(xContentBuilder);
    }

    @Override
    protected void validate(Terminal terminal, OptionSet options, Environment env) throws Exception {
        if ((options.has("i") || options.has("interactive")) && (options.has("a") || options.has("auto"))) {
            throw new UserException(ExitCodes.USAGE, "You can only run the tool in one of [auto] or [interactive] modes");
        }
    }

}
