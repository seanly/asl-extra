package cn.k8ops.ant.asl;

import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AslLogger extends DefaultLogger {

    protected String targetName;

    public AslLogger() {

    }

    public synchronized void targetStarted(BuildEvent event) {
        targetName = extractTargetName(event);
    }

    protected String extractTargetName(BuildEvent event) {
        return event.getTarget().getName();
    }

    public synchronized void targetFinished(BuildEvent event) {
        targetName = null;
    }

    public void messageLogged(BuildEvent event) {

        int priority = event.getPriority();
        if (priority > msgOutputLevel
        || null == event.getMessage()
        || event.getMessage().trim().isEmpty()
        || extractTargetName(event).equalsIgnoreCase("init")) {
            return;
        }

        synchronized (this) {
            if (null != targetName) {

                if (!targetName.equalsIgnoreCase("pipeline")) {
                    out.println(String.format("%n%s:", targetName));
                    targetName = null;
                }
            }
        }

        // Filter out messages based on priority
        if (priority <= msgOutputLevel) {

            StringBuilder message = new StringBuilder();
            if (event.getTask() == null || emacsMode) {
                // emacs mode or there is no task
                message.append(event.getMessage());
            } else {
                // Print out the name of the task if we're in one
                String name = event.getTask().getTaskName();
                String label = "[" + name + "] ";

                int size = LEFT_COLUMN_SIZE - label.length();
                if (name.equalsIgnoreCase("pipeline")) {
                    label = "";
                    size = 0;
                }
                final String prefix = size > 0 ? Stream.generate(() -> " ")
                        .limit(size).collect(Collectors.joining()) + label : label;

                try (BufferedReader r =
                             new BufferedReader(new StringReader(event.getMessage()))) {
                    message.append(r.lines()
                            .collect(Collectors.joining(System.lineSeparator() + prefix, prefix, "")));
                } catch (IOException e) {
                    // shouldn't be possible
                    message.append(label).append(event.getMessage());
                }
            }
            Throwable ex = event.getException();
            if (Project.MSG_DEBUG <= msgOutputLevel && ex != null) {
                message.append(String.format("%n%s: ", ex.getClass().getSimpleName()))
                        .append(StringUtils.getStackTrace(ex));
            }

            String msg = message.toString();
            if (priority != Project.MSG_ERR) {
                printMessage(msg, out, priority);
            } else {
                printMessage(msg, err, priority);
            }
            log(msg);
        }
    }
}
