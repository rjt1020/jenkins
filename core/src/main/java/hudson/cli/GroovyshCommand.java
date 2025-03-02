/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.cli;

import groovy.lang.Binding;
import groovy.lang.Closure;
import hudson.Extension;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import jline.TerminalFactory;
import jline.UnsupportedTerminal;
import org.codehaus.groovy.tools.shell.Groovysh;
import org.codehaus.groovy.tools.shell.IO;
import org.codehaus.groovy.tools.shell.Shell;
import org.codehaus.groovy.tools.shell.util.XmlCommandRegistrar;
import org.kohsuke.args4j.Argument;

/**
 * Executes Groovy shell.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class GroovyshCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return Messages.GroovyshCommand_ShortDescription();
    }

    @Argument(metaVar="ARGS") public List<String> args = new ArrayList<>();

    @Override
    protected int run() {
        // this allows the caller to manipulate the JVM state, so require the admin privilege.
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        // this being remote means no jline capability is available
        System.setProperty("jline.terminal", UnsupportedTerminal.class.getName());
        TerminalFactory.reset();

        StringBuilder commandLine = new StringBuilder();
        for (String arg : args) {
            if (commandLine.length() > 0) {
                commandLine.append(" ");
            }
            commandLine.append(arg);
        }

        Groovysh shell = createShell(stdin, stdout, stderr);
        return shell.run(commandLine.toString());
    }

    @SuppressWarnings("rawtypes")
    protected Groovysh createShell(InputStream stdin, PrintStream stdout,
        PrintStream stderr) {

        Binding binding = new Binding();
        // redirect "println" to the CLI
        binding.setProperty("out", new PrintWriter(stdout,true));
        binding.setProperty("hudson", Jenkins.get()); // backward compatibility
        binding.setProperty("jenkins", Jenkins.get());

        IO io = new IO(new BufferedInputStream(stdin),stdout,stderr);

        final ClassLoader cl = Jenkins.get().pluginManager.uberClassLoader;
        Closure registrar = new Closure(null, null) {
            private static final long serialVersionUID = 1L;

            @SuppressWarnings("unused")
            @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value="UMAC_UNCALLABLE_METHOD_OF_ANONYMOUS_CLASS",justification="Closure invokes this via reflection")
            public Object doCall(Object[] args) {
                assert args.length == 1;
                assert args[0] instanceof Shell;

                Shell shell = (Shell)args[0];
                XmlCommandRegistrar r = new XmlCommandRegistrar(shell, cl);
                r.register(GroovyshCommand.class.getResource("commands.xml"));

                return null;
            }
        };
        Groovysh shell = new Groovysh(cl, binding, io, registrar);
        shell.getImports().add("hudson.model.*");
        return shell;
    }

}
