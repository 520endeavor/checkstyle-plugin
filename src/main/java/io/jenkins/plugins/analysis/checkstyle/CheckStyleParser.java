package io.jenkins.plugins.analysis.checkstyle;

import java.io.IOException;
import java.io.Reader;
import java.util.Optional;

import org.apache.commons.digester3.Digester;
import org.apache.commons.lang.StringUtils;
import org.xml.sax.SAXException;

import edu.hm.hafner.analysis.AbstractParser;
import edu.hm.hafner.analysis.Issue;
import edu.hm.hafner.analysis.IssueBuilder;
import edu.hm.hafner.analysis.Issues;
import edu.hm.hafner.analysis.ParsingCanceledException;
import edu.hm.hafner.analysis.ParsingException;
import edu.hm.hafner.analysis.Priority;

import hudson.plugins.analysis.util.PackageDetectors;
import hudson.plugins.checkstyle.parser.CheckStyle;
import hudson.plugins.checkstyle.parser.Error;
import hudson.plugins.checkstyle.parser.File;

/**
 * A parser for Checkstyle XML files.
 *
 * @author Ulli Hafner
 */
public class CheckStyleParser extends AbstractParser {
    @Override
    public Issues<Issue> parse(final Reader reader, final IssueBuilder builder) throws ParsingCanceledException, ParsingException {
        try {
            Digester digester = new Digester();
            digester.setValidating(false);
            digester.setClassLoader(CheckStyleParser.class.getClassLoader());

            String rootXPath = "checkstyle";
            digester.addObjectCreate(rootXPath, CheckStyle.class);
            digester.addSetProperties(rootXPath);

            String fileXPath = "checkstyle/file";
            digester.addObjectCreate(fileXPath, hudson.plugins.checkstyle.parser.File.class);
            digester.addSetProperties(fileXPath);
            digester.addSetNext(fileXPath, "addFile", hudson.plugins.checkstyle.parser.File.class.getName());

            String bugXPath = "checkstyle/file/error";
            digester.addObjectCreate(bugXPath, Error.class);
            digester.addSetProperties(bugXPath);
            digester.addSetNext(bugXPath, "addError", Error.class.getName());

            CheckStyle checkStyle = digester.parse(reader);
            if (checkStyle == null) {
                throw new SAXException("Input stream is not a Checkstyle file.");
            }

            return convert(checkStyle, builder);
        }
        catch (IOException | SAXException exception) {
            throw new ParsingException(exception);
        }
    }

    /**
     * Converts the internal structure to the annotations API.
     *
     * @param collection
     *         the internal maven module
     *
     * @return a maven module of the annotations API
     */
    private Issues<Issue> convert(final CheckStyle collection, final IssueBuilder builder) {
        Issues<Issue> issues = new Issues<>();

        for (File file : collection.getFiles()) {
            if (isValidWarning(file)) {
                String packageName = PackageDetectors.detectPackageName(file.getName());
                builder.setPackageName(packageName);
                for (Error error : file.getErrors()) {
                    mapPriority(error).ifPresent(
                            (priority) -> {
                                builder.setPriority(priority);

                                String source = error.getSource();
                                builder.setType(getType(source));
                                builder.setCategory(getCategory(source));
                                builder.setMessage(error.getMessage());
                                builder.setLineStart(error.getLine());
                                builder.setFileName(file.getName());
                                builder.setColumnStart(error.getColumn());
                                issues.add(builder.build());
                            }
                    );
                }
            }
        }
        return issues;
    }

    private String getCategory(final String source) {
        return StringUtils.capitalize(getType(StringUtils.substringBeforeLast(source, ".")));
    }

    private String getType(final String source) {
        return StringUtils.substringAfterLast(source, ".");
    }

    private Optional<Priority> mapPriority(final Error error) {
        if ("error".equalsIgnoreCase(error.getSeverity())) {
            return Optional.of(Priority.HIGH);
        }
        if ("warning".equalsIgnoreCase(error.getSeverity())) {
            return Optional.of(Priority.NORMAL);
        }
        if ("info".equalsIgnoreCase(error.getSeverity())) {
            return Optional.of(Priority.LOW);
        }
        return Optional.empty();
    }

    /**
     * Returns {@code true} if this warning is valid or {@code false} if the warning can't be processed by the
     * checkstyle plug-in.
     *
     * @param file
     *         the file to check
     *
     * @return {@code true} if this warning is valid
     */
    private boolean isValidWarning(final File file) {
        return !file.getName().endsWith("package.html");
    }
}

