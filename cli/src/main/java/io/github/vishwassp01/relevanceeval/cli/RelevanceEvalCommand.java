package io.github.vishwassp01.relevanceeval.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

import java.util.concurrent.Callable;

/**
 * Top-level CLI command coordinating search relevance evaluation and diffing subcommands.
 */
@Command(
        name = "relevance-eval",
        mixinStandardHelpOptions = true,
        subcommands = {
                EvaluateCommand.class,
                CompareCommand.class,
                HelpCommand.class
        },
        description = "Search relevance evaluation and regression analysis tool."
)
public class RelevanceEvalCommand implements Callable<Integer> {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new RelevanceEvalCommand()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }
}
