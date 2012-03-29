/**
 * END USER LICENSE AGREEMENT (“EULA”)
 *
 * READ THIS AGREEMENT CAREFULLY (date: 9/13/2011):
 * http://www.akiban.com/licensing/20110913
 *
 * BY INSTALLING OR USING ALL OR ANY PORTION OF THE SOFTWARE, YOU ARE ACCEPTING
 * ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. YOU AGREE THAT THIS
 * AGREEMENT IS ENFORCEABLE LIKE ANY WRITTEN AGREEMENT SIGNED BY YOU.
 *
 * IF YOU HAVE PAID A LICENSE FEE FOR USE OF THE SOFTWARE AND DO NOT AGREE TO
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A) DO
 * NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS OF
 * YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO SIGN
 * FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT. THE
 * LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON ACCEPTANCE
 * BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */

package com.persistit.logging;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * A template for a Persisit log message. See {@link LogBase} for usage. A
 * LogBase instance defines {@link LogItem} instances as fields. At various
 * locations in the the Persistit code base, there may be a call to the
 * <code>LogItem</code> instance's {@link LogItem#log(Object...)} method to
 * record pertinent information in the log. Depending on the configured state of
 * the logging system, the LogItem's <code>log</code> method may either do
 * nothing, or may emit a log message. The HotSpot JIT compiler dynamically
 * removes calls to methods that do nothing, so for disabled
 */
public class PersistitLogMessage {

    /**
     * Interface for a loggable item. Each location in the Persistit code base
     * that potentially emits a log message uses an instance of LogItem. LogItem
     * implementations may do (a) nothing, or (b) call a logging framework to
     * emit the log message. This decision is made by the
     * {@link #configure(PersistitLogger, PersistitLevel, String)} method which
     * is called each time the logging framework is set up or changed.
     * 
     * @author peter
     * 
     */
    public interface LogItem {
        /**
         * @return whether this log item is currently enabled for logging.
         */
        boolean isEnabled();

        /**
         * Construct a log message consisting of this LogItem's template
         * combined with values in thes supplied argument list.
         * 
         * @param args
         *            array of argument values that will be substituted into the
         *            log message
         * @return the message as a string
         */
        String logMessage(Object... args);

        /**
         * Emit a log message, or do nothing if this <code>LogItem</code> is
         * disabled.
         * 
         * @param args
         *            array of argument values that will be substituted into the
         *            log message
         */
        void log(Object... args);

        /**
         * @return This LogItem's log level, one of TRACE, DEBUG, INFO, WARN or
         *         ERROR
         */
        PersistitLevel getLevel();

        /**
         * Modify this <code>LogItem</code> to use the supplied
         * {@link PersistitLogger}. If the <code>logger</code> enables logging
         * for messages having the supplied <code>level</code> then configure
         * this <code>LogItem</code> to emit log messages; else configure it to
         * do nothing, causing the JIT to compile away all instructions for
         * {@link LogItem#log(Object...)} method.
         * 
         * @param logger
         *            The <code>PersistitLogger</code> that emits log messages
         * @param level
         *            The <code>PersistitLevel</code> of this item
         * @param message
         *            A message templace which forms the basis of the log
         *            message
         */
        void configure(PersistitLogger logger, PersistitLevel level, String message);
    }

    /**
     * @return <code>LogItem</code> which may, depending on its configuration,
     *         emit log messages when its {@link LogItem#log(Object...)} method
     *         is called.
     */
    public static LogItem empty() {
        return new LogDispatchHandler();
    }

    /**
     * A {@link LogItem} which may or may not emit log messages, depending on
     * whether it is configured to do so by the
     * {@link #configure(PersistitLogger, PersistitLevel, String)} method.
     * 
     */
    static class LogDispatchHandler implements LogItem {
        private PersistitLogger _logger;
        private PersistitLevel _level;
        private LogItem _dispatch = new Disabled();

        public void configure(final PersistitLogger logger, final PersistitLevel level, final String message) {
            _level = level;
            _logger = logger;
            if (_logger.isLoggable(_level)) {
                _dispatch = new Enabled(logger, level, message);
            } else {
                _dispatch = new Disabled();
            }
        }

        @Override
        public boolean isEnabled() {
            return _dispatch.isEnabled();
        }

        @Override
        public String logMessage(Object... args) {
            return _dispatch.logMessage(args);
        }

        @Override
        public void log(Object... args) {
            _dispatch.log(args);
        }

        public void disable() {
            _dispatch = new Disabled();
        }

        public PersistitLogger getLogger() {
            return _logger;
        }

        public PersistitLevel getLevel() {
            return _level;
        }

    }

    /**
     * Implementation of PersistitLogMessage returns <code>false</code> from its
     * {@link #isEnabled()} method and does nothing in its
     * {@link #log(Object...)} method. HotSpot eliminates calls to the log
     * method.
     */
    static class Disabled implements LogItem {

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public String logMessage(Object... args) {
            return null;
        }

        @Override
        public void log(Object... args) {
        }

        @Override
        public PersistitLevel getLevel() {
            return PersistitLevel.NONE;
        }

        @Override
        public void configure(final PersistitLogger logger, final PersistitLevel level, final String message) {

        }

    }

    /**
     * Implementation of LogMessage that returns <code>true</code> from
     * {@link #isEnabled()} and emits a log message from the
     * {@link #log(Object...)} message.
     * 
     * @author peter
     * 
     */
    static class Enabled implements LogItem {

        private final PersistitLogger _logger;
        private final PersistitLevel _level;
        private final String _message;

        Enabled(final PersistitLogger logger, final PersistitLevel level, final String message) {
            _logger = logger;
            _level = level;
            _message = message;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String logMessage(Object... args) {
            StringBuilder sb = new StringBuilder(String.format("[%s] %s ", Thread.currentThread().getName(), _level));
            try {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof RuntimeException) {
                        args[i] = throwableFormatter((RuntimeException) args[i]);
                    }
                }
                sb.append(String.format(_message, args));
            } catch (Exception e) {
                sb.append("Bad log message ");
                sb.append(_message);
                sb.append(" [");
                for (int i = 0; i < args.length; i++) {
                    if (i != 0) {
                        sb.append(',');
                    }
                    sb.append(args[i]);
                }
                sb.append("]");
            }
            return sb.toString();
        }

        @Override
        public void log(Object... args) {
            _logger.log(_level, logMessage(args));
        }

        public PersistitLogger getLogger() {
            return _logger;
        }

        public PersistitLevel getLevel() {
            return _level;
        }

        public String getMessage() {
            return _message;
        }

        @Override
        public void configure(final PersistitLogger logger, final PersistitLevel level, final String message) {

        }

    }

    static String throwableFormatter(final Throwable t) {
        final StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        final StringBuffer sb = sw.getBuffer();
        for (int p = -1; (p = sb.indexOf("\n", p + 1)) >= 0;) {
            sb.insert(p + 1, "    ");
        }
        return sb.toString();
    }
}