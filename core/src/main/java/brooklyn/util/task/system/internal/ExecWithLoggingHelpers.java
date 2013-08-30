package brooklyn.util.task.system.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import brooklyn.config.ConfigKey;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.internal.ssh.ShellTool;
import brooklyn.util.stream.StreamGobbler;
import brooklyn.util.task.Tasks;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.io.Closeables;

public abstract class ExecWithLoggingHelpers {

    public static final ConfigKey<OutputStream> STDOUT = SshMachineLocation.STDOUT;
    public static final ConfigKey<OutputStream> STDERR = SshMachineLocation.STDERR;
    public static final ConfigKey<Boolean> NO_STDOUT_LOGGING = SshMachineLocation.NO_STDOUT_LOGGING;
    public static final ConfigKey<Boolean> NO_STDERR_LOGGING = SshMachineLocation.NO_STDERR_LOGGING;
    public static final ConfigKey<String> LOG_PREFIX = SshMachineLocation.LOG_PREFIX;

    protected final String shortName;
    protected Logger commandLogger = null;
    
    public interface ExecRunner {
        public int exec(ShellTool ssh, Map<String,?> flags, List<String> cmds, Map<String,?> env);
    }

    protected abstract <T> T execWithTool(MutableMap<String, Object> toolCreationAndConnectionProperties, Function<ShellTool, T> runMethodOnTool);
    
    /** takes a very short name for use in blocking details, e.g. SSH or Process */
    public ExecWithLoggingHelpers(String shortName) {
        this.shortName = shortName;
    }

    public ExecWithLoggingHelpers logger(Logger commandLogger) {
        this.commandLogger = commandLogger;
        return this;
    }
    
    public int execScript(Map<String,?> props, String summaryForLogging, List<String> commands, Map<String,?> env) {
        return execWithLogging(props, summaryForLogging, commands, env, new ExecRunner() {
                @Override public int exec(ShellTool ssh, Map<String, ?> flags, List<String> cmds, Map<String, ?> env) {
                    return ssh.execScript(flags, cmds, env);
                }});
    }

    public int execCommands(Map<String,?> props, String summaryForLogging, List<String> commands, Map<String,?> env) {
        return execWithLogging(props, summaryForLogging, commands, env, new ExecRunner() {
                @Override public int exec(ShellTool tool, Map<String,?> flags, List<String> cmds, Map<String,?> env) {
                    return tool.execCommands(flags, cmds, env);
                }});
    }

    @SuppressWarnings("resource")
    public int execWithLogging(Map<String,?> props, final String summaryForLogging, final List<String> commands, final Map<String,?> env, final ExecRunner execCommand) {
        if (commandLogger!=null && commandLogger.isDebugEnabled()) commandLogger.debug("{}, initiating "+shortName.toLowerCase()+" on machine {}: {}", new Object[] {summaryForLogging, this, commands});

        if (commands.isEmpty()) {
            if (commandLogger!=null && commandLogger.isDebugEnabled())
                commandLogger.debug("{}, on machine {} ,ending: no commands to run", summaryForLogging, this);
            return 0;
        }

        final ConfigBag execFlags = new ConfigBag().putAll(props);
        // some props get overridden in execFlags, so remove them from the tool flags
        final ConfigBag toolFlags = new ConfigBag().putAll(props).removeAll(LOG_PREFIX, STDOUT, STDERR, ShellTool.PROP_NO_EXTRA_OUTPUT);

        PipedOutputStream outO = null;
        PipedOutputStream outE = null;
        StreamGobbler gO=null, gE=null;
        try {
            preExecChecks();
            
            String logPrefix = execFlags.get(LOG_PREFIX);
            if (logPrefix==null) logPrefix = constructDefaultLoggingPrefix(execFlags);

            if (!execFlags.get(NO_STDOUT_LOGGING)) {
                PipedInputStream insO = new PipedInputStream();
                outO = new PipedOutputStream(insO);

                String stdoutLogPrefix = "["+(logPrefix != null ? logPrefix+":stdout" : "stdout")+"] ";
                gO = new StreamGobbler(insO, execFlags.get(STDOUT), commandLogger).setLogPrefix(stdoutLogPrefix);
                gO.start();

                execFlags.put(STDOUT, outO);
            }

            if (!execFlags.get(NO_STDERR_LOGGING)) {
                PipedInputStream insE = new PipedInputStream();
                outE = new PipedOutputStream(insE);

                String stderrLogPrefix = "["+(logPrefix != null ? logPrefix+":stderr" : "stderr")+"] ";
                gE = new StreamGobbler(insE, execFlags.get(STDERR), commandLogger).setLogPrefix(stderrLogPrefix);
                gE.start();

                execFlags.put(STDERR, outE);
            }

            Tasks.setBlockingDetails(shortName+" executing, "+summaryForLogging);
            try {
                return execWithTool(MutableMap.copyOf(toolFlags.getAllConfig()), new Function<ShellTool, Integer>() {
                    public Integer apply(ShellTool tool) {
                        int result = execCommand.exec(tool, MutableMap.copyOf(execFlags.getAllConfig()), commands, env);
                        if (commandLogger!=null && commandLogger.isDebugEnabled()) 
                            commandLogger.debug("{}, on machine {}, completed: return status {}", new Object[] {summaryForLogging, this, result});
                        return result;
                    }});

            } finally {
                Tasks.setBlockingDetails(null);
            }

        } catch (IOException e) {
            if (commandLogger!=null && commandLogger.isDebugEnabled()) 
                commandLogger.debug("{}, on machine {}, failed: {}", new Object[] {summaryForLogging, this, e});
            throw Throwables.propagate(e);
        } finally {
            // Must close the pipedOutStreams, otherwise input will never read -1 so StreamGobbler thread would never die
            if (outO!=null) try { outO.flush(); } catch (IOException e) {}
            if (outE!=null) try { outE.flush(); } catch (IOException e) {}
            Closeables.closeQuietly(outO);
            Closeables.closeQuietly(outE);

            try {
                if (gE!=null) { gE.join(); }
                if (gO!=null) { gO.join(); }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Throwables.propagate(e);
            }
        }

    }

    protected abstract void preExecChecks();
    protected abstract String constructDefaultLoggingPrefix(ConfigBag execFlags);
    
}
