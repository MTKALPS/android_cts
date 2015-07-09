package com.android.cts.tradefed.testtype;

import com.android.cts.tradefed.build.CtsBuildHelper;
import com.android.cts.util.AbiUtils;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunInterruptedException;
import com.android.tradefed.util.RunUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Test runner for dEQP tests
 *
 * Supports running drawElements Quality Program tests found under external/deqp.
 */
public class DeqpTestRunner implements IBuildReceiver, IDeviceTest, IRemoteTest {

    private static final String DEQP_ONDEVICE_APK = "com.drawelements.deqp.apk";
    private static final String DEQP_ONDEVICE_PKG = "com.drawelements.deqp";
    private static final String INCOMPLETE_LOG_MESSAGE = "Crash: Incomplete test log";
    private static final String SKIPPED_INSTANCE_LOG_MESSAGE = "Configuration skipped";
    private static final String NOT_EXECUTABLE_LOG_MESSAGE = "Abort: Test cannot be executed";
    private static final String CASE_LIST_FILE_NAME = "/sdcard/dEQP-TestCaseList.txt";
    private static final String LOG_FILE_NAME = "/sdcard/TestLog.qpa";
    public static final String FEATURE_LANDSCAPE = "android.hardware.screen.landscape";
    public static final String FEATURE_PORTRAIT = "android.hardware.screen.portrait";

    private static final int TESTCASE_BATCH_LIMIT = 1000;
    private static final BatchRunConfiguration DEFAULT_CONFIG =
        new BatchRunConfiguration("rgba8888d24s8", "unspecified", "window");

    private static final int UNRESPOSIVE_CMD_TIMEOUT_MS = 60000; // one minute
    private static final int FILE_REMOVE_RETRY_INTERVAL = 2000; // 2 seconds

    private final String mPackageName;
    private final String mName;
    private final Collection<TestIdentifier> mRemainingTests;
    private final Map<TestIdentifier, Set<BatchRunConfiguration>> mTestInstances;
    private final TestInstanceResultListener mInstanceListerner = new TestInstanceResultListener();
    private final Map<TestIdentifier, Integer> mTestInstabilityRatings;
    private IAbi mAbi;
    private CtsBuildHelper mCtsBuild;
    private boolean mLogData = false;
    private ITestDevice mDevice;
    private Set<String> mDeviceFeatures;
    private Map<String, Boolean> mConfigQuerySupportCache = new HashMap<>();
    private IRunUtil mRunUtil = RunUtil.getDefault();
    private ISleepProvider mSleepProvider = new SleepProvider();

    private IRecovery mDeviceRecovery = new Recovery();
    {
        mDeviceRecovery.setSleepProvider(new SleepProvider());
    }

    public DeqpTestRunner(String packageName, String name, Collection<TestIdentifier> tests,
            Map<TestIdentifier, List<Map<String,String>>> testInstances) {
        mPackageName = packageName;
        mName = name;
        mRemainingTests = new LinkedList<>(tests); // avoid modifying arguments
        mTestInstances = parseTestInstances(tests, testInstances);
        mTestInstabilityRatings = new HashMap<>();
    }

    /**
     * @param abi the ABI to run the test on
     */
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = CtsBuildHelper.createBuildHelper(buildInfo);
    }

    /**
     * Set the CTS build container.
     * <p/>
     * Exposed so unit tests can mock the provided build.
     *
     * @param buildHelper
     */
    public void setBuildHelper(CtsBuildHelper buildHelper) {
        mCtsBuild = buildHelper;
    }

    /**
     * Enable or disable raw dEQP test log collection.
     */
    public void setCollectLogs(boolean logData) {
        mLogData = logData;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * Set recovery handler.
     *
     * Exposed for unit testing.
     */
    public void setRecovery(IRecovery deviceRecovery) {
        mDeviceRecovery = deviceRecovery;
    }

    /**
     * Sets the sleep provider DeqpTestRunner works on.
     *
     * Exposed for unit testing.
     */
    public void setSleepProvider(ISleepProvider sleepProvider) {
        mSleepProvider = sleepProvider;
    }

    /**
     * Set IRunUtil.
     *
     * Exposed for unit testing.
     */
    public void setRunUtil(IRunUtil runUtil) {
        mRunUtil = runUtil;
    }

    private static final class CapabilityQueryFailureException extends Exception {
    };

    /**
     * Test configuration of dEPQ test instance execution.
     * Exposed for unit testing
     */
    public static final class BatchRunConfiguration {
        public static final String ROTATION_UNSPECIFIED = "unspecified";
        public static final String ROTATION_PORTRAIT = "0";
        public static final String ROTATION_LANDSCAPE = "90";
        public static final String ROTATION_REVERSE_PORTRAIT = "180";
        public static final String ROTATION_REVERSE_LANDSCAPE = "270";

        private final String mGlConfig;
        private final String mRotation;
        private final String mSurfaceType;

        public BatchRunConfiguration(String glConfig, String rotation, String surfaceType) {
            mGlConfig = glConfig;
            mRotation = rotation;
            mSurfaceType = surfaceType;
        }

        /**
         * Get string that uniquely identifies this config
         */
        public String getId() {
            return String.format("{glformat=%s,rotation=%s,surfacetype=%s}",
                    mGlConfig, mRotation, mSurfaceType);
        }

        /**
         * Get the GL config used in this configuration.
         */
        public String getGlConfig() {
            return mGlConfig;
        }

        /**
         * Get the screen rotation used in this configuration.
         */
        public String getRotation() {
            return mRotation;
        }

        /**
         * Get the surface type used in this configuration.
         */
        public String getSurfaceType() {
            return mSurfaceType;
        }

        @Override
        public boolean equals(Object other) {
            if (other == null) {
                return false;
            } else if (!(other instanceof BatchRunConfiguration)) {
                return false;
            } else {
                return getId().equals(((BatchRunConfiguration)other).getId());
            }
        }

        @Override
        public int hashCode() {
            return getId().hashCode();
        }
    }

    /**
     * dEQP test instance listerer and invocation result forwarded
     */
    private class TestInstanceResultListener {
        private ITestInvocationListener mSink;
        private BatchRunConfiguration mRunConfig;

        private TestIdentifier mCurrentTestId;
        private boolean mGotTestResult;
        private String mCurrentTestLog;

        private class PendingResult
        {
            boolean allInstancesPassed;
            Map<BatchRunConfiguration, String> testLogs;
            Map<BatchRunConfiguration, String> errorMessages;
            Set<BatchRunConfiguration> remainingConfigs;
        };
        private final Map<TestIdentifier, PendingResult> mPendingResults = new HashMap<>();

        public void setSink(ITestInvocationListener sink) {
            mSink = sink;
        }

        public void setCurrentConfig(BatchRunConfiguration runConfig) {
            mRunConfig = runConfig;
        }

        /**
         * Get currently processed test id, or null if not currently processing a test case
         */
        public TestIdentifier getCurrentTestId() {
            return mCurrentTestId;
        }

        /**
         * Forward result to sink
         */
        private void forwardFinalizedPendingResult(TestIdentifier testId) {
            if (mRemainingTests.contains(testId)) {
                final PendingResult result = mPendingResults.get(testId);

                mPendingResults.remove(testId);
                mRemainingTests.remove(testId);

                // Forward results to the sink
                mSink.testStarted(testId);

                // Test Log
                if (mLogData) {
                    for (Map.Entry<BatchRunConfiguration, String> entry :
                            result.testLogs.entrySet()) {
                        final ByteArrayInputStreamSource source
                                = new ByteArrayInputStreamSource(entry.getValue().getBytes());

                        mSink.testLog(testId.getClassName() + "." + testId.getTestName() + "@"
                                + entry.getKey().getId(), LogDataType.XML, source);

                        source.cancel();
                    }
                }

                // Error message
                if (!result.allInstancesPassed) {
                    final StringBuilder errorLog = new StringBuilder();

                    for (Map.Entry<BatchRunConfiguration, String> entry :
                            result.errorMessages.entrySet()) {
                        if (errorLog.length() > 0) {
                            errorLog.append('\n');
                        }
                        errorLog.append(String.format("=== with config %s ===\n",
                                entry.getKey().getId()));
                        errorLog.append(entry.getValue());
                    }

                    mSink.testFailed(testId, errorLog.toString());
                }

                final Map<String, String> emptyMap = Collections.emptyMap();
                mSink.testEnded(testId, emptyMap);
            }
        }

        /**
         * Declare existence of a test and instances
         */
        public void setTestInstances(TestIdentifier testId, Set<BatchRunConfiguration> configs) {
            // Test instances cannot change at runtime, ignore if we have already set this
            if (!mPendingResults.containsKey(testId)) {
                final PendingResult pendingResult = new PendingResult();
                pendingResult.allInstancesPassed = true;
                pendingResult.testLogs = new LinkedHashMap<>();
                pendingResult.errorMessages = new LinkedHashMap<>();
                pendingResult.remainingConfigs = new HashSet<>(configs); // avoid mutating argument
                mPendingResults.put(testId, pendingResult);
            }
        }

        /**
         * Query if test instance has not yet been executed
         */
        public boolean isPendingTestInstance(TestIdentifier testId,
                BatchRunConfiguration config) {
            final PendingResult result = mPendingResults.get(testId);
            if (result == null) {
                // test is not in the current working batch of the runner, i.e. it cannot be
                // "partially" completed.
                if (!mRemainingTests.contains(testId)) {
                    // The test has been fully executed. Not pending.
                    return false;
                } else {
                    // Test has not yet been executed. Check if such instance exists
                    return mTestInstances.get(testId).contains(config);
                }
            } else {
                // could be partially completed, check this particular config
                return result.remainingConfigs.contains(config);
            }
        }

        /**
         * Fake execution of an instance with current config
         */
        public void skipTest(TestIdentifier testId) {
            final PendingResult result = mPendingResults.get(testId);

            result.errorMessages.put(mRunConfig, SKIPPED_INSTANCE_LOG_MESSAGE);
            result.remainingConfigs.remove(mRunConfig);

            // Pending result finished, report result
            if (result.remainingConfigs.isEmpty()) {
                forwardFinalizedPendingResult(testId);
            }
        }

        /**
         * Fake failure of an instance with current config
         */
        public void abortTest(TestIdentifier testId, String errorMessage) {
            final PendingResult result = mPendingResults.get(testId);

            // Mark as executed
            result.allInstancesPassed = false;
            result.errorMessages.put(mRunConfig, errorMessage);
            result.remainingConfigs.remove(mRunConfig);

            // Pending result finished, report result
            if (result.remainingConfigs.isEmpty()) {
                forwardFinalizedPendingResult(testId);
            }

            if (testId.equals(mCurrentTestId)) {
                mCurrentTestId = null;
            }
        }

        /**
         * Handles beginning of dEQP session.
         */
        private void handleBeginSession(Map<String, String> values) {
            // ignore
        }

        /**
         * Handles end of dEQP session.
         */
        private void handleEndSession(Map<String, String> values) {
            // ignore
        }

        /**
         * Handles beginning of dEQP testcase.
         */
        private void handleBeginTestCase(Map<String, String> values) {
            mCurrentTestId = pathToIdentifier(values.get("dEQP-BeginTestCase-TestCasePath"));
            mCurrentTestLog = "";
            mGotTestResult = false;

            // mark instance as started
            if (mPendingResults.get(mCurrentTestId) != null) {
                mPendingResults.get(mCurrentTestId).remainingConfigs.remove(mRunConfig);
            } else {
                CLog.w("Got unexpected start of %s", mCurrentTestId);
            }
        }

        /**
         * Handles end of dEQP testcase.
         */
        private void handleEndTestCase(Map<String, String> values) {
            final PendingResult result = mPendingResults.get(mCurrentTestId);

            if (result != null) {
                if (!mGotTestResult) {
                    result.allInstancesPassed = false;
                    result.errorMessages.put(mRunConfig, INCOMPLETE_LOG_MESSAGE);
                }

                if (mLogData && mCurrentTestLog != null && mCurrentTestLog.length() > 0) {
                    result.testLogs.put(mRunConfig, mCurrentTestLog);
                }

                // Pending result finished, report result
                if (result.remainingConfigs.isEmpty()) {
                    forwardFinalizedPendingResult(mCurrentTestId);
                }
            } else {
                CLog.w("Got unexpected end of %s", mCurrentTestId);
            }
            mCurrentTestId = null;
        }

        /**
         * Handles dEQP testcase result.
         */
        private void handleTestCaseResult(Map<String, String> values) {
            String code = values.get("dEQP-TestCaseResult-Code");
            String details = values.get("dEQP-TestCaseResult-Details");

            if (mPendingResults.get(mCurrentTestId) == null) {
                CLog.w("Got unexpected result for %s", mCurrentTestId);
                mGotTestResult = true;
                return;
            }

            if (code.compareTo("Pass") == 0) {
                mGotTestResult = true;
            } else if (code.compareTo("NotSupported") == 0) {
                mGotTestResult = true;
            } else if (code.compareTo("QualityWarning") == 0) {
                mGotTestResult = true;
            } else if (code.compareTo("CompatibilityWarning") == 0) {
                mGotTestResult = true;
            } else if (code.compareTo("Fail") == 0 || code.compareTo("ResourceError") == 0
                    || code.compareTo("InternalError") == 0 || code.compareTo("Crash") == 0
                    || code.compareTo("Timeout") == 0) {
                mPendingResults.get(mCurrentTestId).allInstancesPassed = false;
                mPendingResults.get(mCurrentTestId)
                        .errorMessages.put(mRunConfig, code + ": " + details);
                mGotTestResult = true;
            } else {
                String codeError = "Unknown result code: " + code;
                mPendingResults.get(mCurrentTestId).allInstancesPassed = false;
                mPendingResults.get(mCurrentTestId)
                        .errorMessages.put(mRunConfig, codeError + ": " + details);
                mGotTestResult = true;
            }
        }

        /**
         * Handles terminated dEQP testcase.
         */
        private void handleTestCaseTerminate(Map<String, String> values) {
            final PendingResult result = mPendingResults.get(mCurrentTestId);

            if (result != null) {
                String reason = values.get("dEQP-TerminateTestCase-Reason");
                mPendingResults.get(mCurrentTestId).allInstancesPassed = false;
                mPendingResults.get(mCurrentTestId)
                        .errorMessages.put(mRunConfig, "Terminated: " + reason);

                // Pending result finished, report result
                if (result.remainingConfigs.isEmpty()) {
                    forwardFinalizedPendingResult(mCurrentTestId);
                }
            } else {
                CLog.w("Got unexpected termination of %s", mCurrentTestId);
            }

            mCurrentTestId = null;
            mGotTestResult = true;
        }

        /**
         * Handles dEQP testlog data.
         */
        private void handleTestLogData(Map<String, String> values) {
            mCurrentTestLog = mCurrentTestLog + values.get("dEQP-TestLogData-Log");
        }

        /**
         * Handles new instrumentation status message.
         */
        public void handleStatus(Map<String, String> values) {
            String eventType = values.get("dEQP-EventType");

            if (eventType == null) {
                return;
            }

            if (eventType.compareTo("BeginSession") == 0) {
                handleBeginSession(values);
            } else if (eventType.compareTo("EndSession") == 0) {
                handleEndSession(values);
            } else if (eventType.compareTo("BeginTestCase") == 0) {
                handleBeginTestCase(values);
            } else if (eventType.compareTo("EndTestCase") == 0) {
                handleEndTestCase(values);
            } else if (eventType.compareTo("TestCaseResult") == 0) {
                handleTestCaseResult(values);
            } else if (eventType.compareTo("TerminateTestCase") == 0) {
                handleTestCaseTerminate(values);
            } else if (eventType.compareTo("TestLogData") == 0) {
                handleTestLogData(values);
            }
        }

        /**
         * Signal listener that batch ended and forget incomplete results.
         */
        public void endBatch() {
            // end open test if when stream ends
            if (mCurrentTestId != null) {
                // Current instance was removed from remainingConfigs when case
                // started. Mark current instance as pending.
                if (mPendingResults.get(mCurrentTestId) != null) {
                    mPendingResults.get(mCurrentTestId).remainingConfigs.add(mRunConfig);
                } else {
                    CLog.w("Got unexpected internal state of %s", mCurrentTestId);
                }
            }
            mCurrentTestId = null;
        }
    }

    /**
     * dEQP instrumentation parser
     */
    private static class InstrumentationParser extends MultiLineReceiver {
        private TestInstanceResultListener mListener;

        private Map<String, String> mValues;
        private String mCurrentName;
        private String mCurrentValue;
        private int mResultCode;
        private boolean mGotExitValue = false;


        public InstrumentationParser(TestInstanceResultListener listener) {
            mListener = listener;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void processNewLines(String[] lines) {
            for (String line : lines) {
                if (mValues == null) mValues = new HashMap<String, String>();

                if (line.startsWith("INSTRUMENTATION_STATUS_CODE: ")) {
                    if (mCurrentName != null) {
                        mValues.put(mCurrentName, mCurrentValue);

                        mCurrentName = null;
                        mCurrentValue = null;
                    }

                    mListener.handleStatus(mValues);
                    mValues = null;
                } else if (line.startsWith("INSTRUMENTATION_STATUS: dEQP-")) {
                    if (mCurrentName != null) {
                        mValues.put(mCurrentName, mCurrentValue);

                        mCurrentValue = null;
                        mCurrentName = null;
                    }

                    String prefix = "INSTRUMENTATION_STATUS: ";
                    int nameBegin = prefix.length();
                    int nameEnd = line.indexOf('=');
                    int valueBegin = nameEnd + 1;

                    mCurrentName = line.substring(nameBegin, nameEnd);
                    mCurrentValue = line.substring(valueBegin);
                } else if (line.startsWith("INSTRUMENTATION_CODE: ")) {
                    try {
                        mResultCode = Integer.parseInt(line.substring(22));
                        mGotExitValue = true;
                    } catch (NumberFormatException ex) {
                        CLog.w("Instrumentation code format unexpected");
                    }
                } else if (mCurrentValue != null) {
                    mCurrentValue = mCurrentValue + line;
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void done() {
            if (mCurrentName != null) {
                mValues.put(mCurrentName, mCurrentValue);

                mCurrentName = null;
                mCurrentValue = null;
            }

            if (mValues != null) {
                mListener.handleStatus(mValues);
                mValues = null;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isCancelled() {
            return false;
        }

        /**
         * Returns whether target instrumentation exited normally.
         */
        public boolean wasSuccessful() {
            return mGotExitValue;
        }

        /**
         * Returns Instrumentation return code
         */
        public int getResultCode() {
            return mResultCode;
        }
    }

    /**
     * dEQP platfom query instrumentation parser
     */
    private static class PlatformQueryInstrumentationParser extends MultiLineReceiver {
        private Map<String,String> mResultMap = new LinkedHashMap<>();
        private int mResultCode;
        private boolean mGotExitValue = false;

        /**
         * {@inheritDoc}
         */
        @Override
        public void processNewLines(String[] lines) {
            for (String line : lines) {
                if (line.startsWith("INSTRUMENTATION_RESULT: ")) {
                    final String parts[] = line.substring(24).split("=",2);
                    if (parts.length == 2) {
                        mResultMap.put(parts[0], parts[1]);
                    } else {
                        CLog.w("Instrumentation status format unexpected");
                    }
                } else if (line.startsWith("INSTRUMENTATION_CODE: ")) {
                    try {
                        mResultCode = Integer.parseInt(line.substring(22));
                        mGotExitValue = true;
                    } catch (NumberFormatException ex) {
                        CLog.w("Instrumentation code format unexpected");
                    }
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isCancelled() {
            return false;
        }

        /**
         * Returns whether target instrumentation exited normally.
         */
        public boolean wasSuccessful() {
            return mGotExitValue;
        }

        /**
         * Returns Instrumentation return code
         */
        public int getResultCode() {
            return mResultCode;
        }

        public Map<String,String> getResultMap() {
            return mResultMap;
        }
    }

    /**
     * Interface for sleeping.
     *
     * Exposed for unit testing
     */
    public static interface ISleepProvider {
        public void sleep(int milliseconds);
    };

    private static class SleepProvider implements ISleepProvider {
        public void sleep(int milliseconds) {
            try {
                Thread.sleep(milliseconds);
            } catch (InterruptedException ex) {
            }
        }
    };

    /**
     * Interface for failure recovery.
     *
     * Exposed for unit testing
     */
    public static interface IRecovery {
        /**
         * Sets the sleep provider IRecovery works on
         */
        public void setSleepProvider(ISleepProvider sleepProvider);

        /**
         * Sets the device IRecovery works on
         */
        public void setDevice(ITestDevice device);

        /**
         * Informs Recovery that test execution has progressed since the last recovery
         */
        public void onExecutionProgressed();

        /**
         * Tries to recover device after failed refused connection.
         *
         * @throws DeviceNotAvailableException if recovery did not succeed
         */
        public void recoverConnectionRefused() throws DeviceNotAvailableException;

        /**
         * Tries to recover device after abnormal termination of a command or a test run execution
         * or link failure.
         *
         * @throws DeviceNotAvailableException if recovery did not succeed
         */
        public void recoverCommandNotCompleted() throws DeviceNotAvailableException;
    };

    /**
     * State machine for execution failure recovery.
     *
     * Exposed for unit testing
     */
    public static class Recovery implements IRecovery {
        private int RETRY_COOLDOWN_MS = 6000; // 6 seconds
        private int PROCESS_KILL_WAIT_MS = 1000; // 1 second

        private static enum MachineState {
            WAIT, // recover by waiting
            RECOVER, // recover by calling recover()
            REBOOT, // recover by rebooting
            FAIL, // cannot recover
        };

        private MachineState mState = MachineState.WAIT;
        private ITestDevice mDevice;
        private ISleepProvider mSleepProvider;

        private static class ProcessKillFailureException extends Exception {
        }

        /**
         * {@inheritDoc}
         */
        public void setSleepProvider(ISleepProvider sleepProvider) {
            mSleepProvider = sleepProvider;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setDevice(ITestDevice device) {
            mDevice = device;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onExecutionProgressed() {
            mState = MachineState.WAIT;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void recoverConnectionRefused() throws DeviceNotAvailableException {
            switch (mState) {
                case WAIT: // not a valid stratedy for connection refusal, fallthrough
                case RECOVER:
                    // First failure, just try to recover
                    CLog.w("ADB connection failed, trying to recover");
                    mState = MachineState.REBOOT; // the next step is to reboot

                    try {
                        recoverDevice();
                    } catch (DeviceNotAvailableException ex) {
                        // chain forward
                        recoverConnectionRefused();
                    }
                    break;

                case REBOOT:
                    // Second failure in a row, try to reboot
                    CLog.w("ADB connection failed after recovery, rebooting device");
                    mState = MachineState.FAIL; // the next step is to fail

                    try {
                        rebootDevice();
                    } catch (DeviceNotAvailableException ex) {
                        // chain forward
                        recoverConnectionRefused();
                    }
                    break;

                case FAIL:
                    // Third failure in a row, just fail
                    CLog.w("Cannot recover ADB connection");
                    throw new DeviceNotAvailableException("failed to connect after reboot");
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void recoverCommandNotCompleted() throws DeviceNotAvailableException {
            switch (mState) {
                case WAIT:
                    // First failure, just try to wait and try again
                    CLog.w("ADB link failed, retrying after a cooldown period");
                    mState = MachineState.RECOVER; // the next step is to recover the device

                    waitCooldown();

                    // even if the link to deqp on-device process was killed, the process might
                    // still be alive. Locate and terminate such unwanted processes.
                    try {
                        killDeqpProcess();
                    } catch (DeviceNotAvailableException ex) {
                        // chain forward
                        recoverCommandNotCompleted();
                    } catch (ProcessKillFailureException ex) {
                        // chain forward
                        recoverCommandNotCompleted();
                    }
                    break;

                case RECOVER:
                    // Second failure, just try to recover
                    CLog.w("ADB link failed, trying to recover");
                    mState = MachineState.REBOOT; // the next step is to reboot

                    try {
                        recoverDevice();
                        killDeqpProcess();
                    } catch (DeviceNotAvailableException ex) {
                        // chain forward
                        recoverCommandNotCompleted();
                    } catch (ProcessKillFailureException ex) {
                        // chain forward
                        recoverCommandNotCompleted();
                    }
                    break;

                case REBOOT:
                    // Third failure in a row, try to reboot
                    CLog.w("ADB link failed after recovery, rebooting device");
                    mState = MachineState.FAIL; // the next step is to fail

                    try {
                        rebootDevice();
                    } catch (DeviceNotAvailableException ex) {
                        // chain forward
                        recoverCommandNotCompleted();
                    }
                    break;

                case FAIL:
                    // Fourth failure in a row, just fail
                    CLog.w("Cannot recover ADB connection");
                    throw new DeviceNotAvailableException("link killed after reboot");
            }
        }

        private void waitCooldown() {
            mSleepProvider.sleep(RETRY_COOLDOWN_MS);
        }

        private Iterable<Integer> getDeqpProcessPids() throws DeviceNotAvailableException {
            final List<Integer> pids = new ArrayList<Integer>(2);
            final String processes = mDevice.executeShellCommand("ps | grep com.drawelements");
            final String[] lines = processes.split("(\\r|\\n)+");
            for (String line : lines) {
                final String[] fields = line.split("\\s+");
                if (fields.length < 2) {
                    continue;
                }

                try {
                    final int processId = Integer.parseInt(fields[1], 10);
                    pids.add(processId);
                } catch (NumberFormatException ex) {
                    continue;
                }
            }
            return pids;
        }

        private void killDeqpProcess() throws DeviceNotAvailableException,
                ProcessKillFailureException {
            for (Integer processId : getDeqpProcessPids()) {
                mDevice.executeShellCommand(String.format("kill -9 %d", processId));
            }

            mSleepProvider.sleep(PROCESS_KILL_WAIT_MS);

            // check that processes actually died
            if (getDeqpProcessPids().iterator().hasNext()) {
                // a process is still alive, killing failed
                throw new ProcessKillFailureException();
            }
        }

        public void recoverDevice() throws DeviceNotAvailableException {
            // Work around the API. We need to call recoverDevice() on the test device and
            // we know that mDevice is a TestDevice. However even though the recoverDevice()
            // method is public suggesting it should be publicly accessible, the class itself
            // and its super-interface (IManagedTestDevice) are package-private.
            final Method recoverDeviceMethod;
            try {
                recoverDeviceMethod = mDevice.getClass().getMethod("recoverDevice");
                recoverDeviceMethod.setAccessible(true);
            } catch (NoSuchMethodException ex) {
                throw new AssertionError("Test device must have recoverDevice()");
            }

            try {
                recoverDeviceMethod.invoke(mDevice);
            } catch (InvocationTargetException ex) {
                if (ex.getCause() instanceof DeviceNotAvailableException) {
                    throw (DeviceNotAvailableException)ex.getCause();
                } else if (ex.getCause() instanceof RuntimeException) {
                    throw (RuntimeException)ex.getCause();
                } else {
                    throw new AssertionError("unexpected throw", ex);
                }
            } catch (IllegalAccessException ex) {
                throw new AssertionError("unexpected throw", ex);
            }
        }

        private void rebootDevice() throws DeviceNotAvailableException {
            mDevice.reboot();
        }
    };

    /**
     * Parse map of instance arguments to map of BatchRunConfigurations
     */
    private static Map<TestIdentifier, Set<BatchRunConfiguration>> parseTestInstances(
            Collection<TestIdentifier> tests,
            Map<TestIdentifier, List<Map<String,String>>> testInstances) {
        final Map<TestIdentifier, Set<BatchRunConfiguration>> instances = new HashMap<>();
        for (final TestIdentifier test : tests) {
            final Set<BatchRunConfiguration> testInstanceSet = new LinkedHashSet<>();
            if (testInstances.get(test).isEmpty()) {
                // no instances defined, use default
                testInstanceSet.add(DEFAULT_CONFIG);
            } else {
                for (Map<String, String> instanceArgs : testInstances.get(test)) {
                    testInstanceSet.add(parseRunConfig(instanceArgs));
                }
            }
            instances.put(test, testInstanceSet);
        }
        return instances;
    }

    private static BatchRunConfiguration parseRunConfig(Map<String,String> instanceArguments) {
        final String glConfig;
        final String rotation;
        final String surfaceType;

        if (instanceArguments.containsKey("glconfig")) {
            glConfig = instanceArguments.get("glconfig");
        } else {
            glConfig = DEFAULT_CONFIG.getGlConfig();
        }
        if (instanceArguments.containsKey("rotation")) {
            rotation = instanceArguments.get("rotation");
        } else {
            rotation = DEFAULT_CONFIG.getRotation();
        }
        if (instanceArguments.containsKey("surfaceType")) {
            surfaceType = instanceArguments.get("surfaceType");
        } else {
            surfaceType = DEFAULT_CONFIG.getSurfaceType();
        }

        return new BatchRunConfiguration(glConfig, rotation, surfaceType);
    }

    private Set<BatchRunConfiguration> getTestRunConfigs (TestIdentifier testId) {
        return mTestInstances.get(testId);
    }

    /**
     * Converts dEQP testcase path to TestIdentifier.
     */
    private static TestIdentifier pathToIdentifier(String testPath) {
        String[] components = testPath.split("\\.");
        String name = components[components.length - 1];
        String className = null;

        for (int i = 0; i < components.length - 1; i++) {
            if (className == null) {
                className = components[i];
            } else {
                className = className + "." + components[i];
            }
        }

        return new TestIdentifier(className, name);
    }

    private String getId() {
        return AbiUtils.createId(mAbi.getName(), mPackageName);
    }

    /**
     * Generates tescase trie from dEQP testcase paths. Used to define which testcases to execute.
     */
    private static String generateTestCaseTrieFromPaths(Collection<String> tests) {
        String result = "{";
        boolean first = true;

        // Add testcases to results
        for (Iterator<String> iter = tests.iterator(); iter.hasNext();) {
            String test = iter.next();
            String[] components = test.split("\\.");

            if (components.length == 1) {
                if (!first) {
                    result = result + ",";
                }
                first = false;

                result += components[0];
                iter.remove();
            }
        }

        if (!tests.isEmpty()) {
            HashMap<String, ArrayList<String> > testGroups = new HashMap<>();

            // Collect all sub testgroups
            for (String test : tests) {
                String[] components = test.split("\\.");
                ArrayList<String> testGroup = testGroups.get(components[0]);

                if (testGroup == null) {
                    testGroup = new ArrayList<String>();
                    testGroups.put(components[0], testGroup);
                }

                testGroup.add(test.substring(components[0].length()+1));
            }

            for (String testGroup : testGroups.keySet()) {
                if (!first) {
                    result = result + ",";
                }

                first = false;
                result = result + testGroup
                        + generateTestCaseTrieFromPaths(testGroups.get(testGroup));
            }
        }

        return result + "}";
    }

    /**
     * Generates testcase trie from TestIdentifiers.
     */
    private static String generateTestCaseTrie(Collection<TestIdentifier> tests) {
        ArrayList<String> testPaths = new ArrayList<String>();

        for (TestIdentifier test : tests) {
            testPaths.add(test.getClassName() + "." + test.getTestName());
        }

        return generateTestCaseTrieFromPaths(testPaths);
    }

    private static class TestBatch {
        public BatchRunConfiguration config;
        public List<TestIdentifier> tests;
    }

    private TestBatch selectRunBatch() {
        return selectRunBatch(mRemainingTests, null);
    }

    /**
     * Creates a TestBatch from the given tests or null if not tests remaining.
     *
     *  @param pool List of tests to select from
     *  @param requiredConfig Select only instances with pending requiredConfig, or null to select
     *         any run configuration.
     */
    private TestBatch selectRunBatch(Collection<TestIdentifier> pool,
            BatchRunConfiguration requiredConfig) {
        // select one test (leading test) that is going to be executed and then pack along as many
        // other compatible instances as possible.

        TestIdentifier leadingTest = null;
        for (TestIdentifier test : pool) {
            if (!mRemainingTests.contains(test)) {
                continue;
            }
            if (requiredConfig != null &&
                    !mInstanceListerner.isPendingTestInstance(test, requiredConfig)) {
                continue;
            }
            leadingTest = test;
            break;
        }

        // no remaining tests?
        if (leadingTest == null) {
            return null;
        }

        BatchRunConfiguration leadingTestConfig = null;
        if (requiredConfig != null) {
            leadingTestConfig = requiredConfig;
        } else {
            for (BatchRunConfiguration runConfig : getTestRunConfigs(leadingTest)) {
                if (mInstanceListerner.isPendingTestInstance(leadingTest, runConfig)) {
                    leadingTestConfig = runConfig;
                    break;
                }
            }
        }

        // test pending <=> test has a pending config
        if (leadingTestConfig == null) {
            throw new AssertionError("search postcondition failed");
        }

        final int leadingInstability = getTestInstabilityRating(leadingTest);

        final TestBatch runBatch = new TestBatch();
        runBatch.config = leadingTestConfig;
        runBatch.tests = new ArrayList<>();
        runBatch.tests.add(leadingTest);

        for (TestIdentifier test : pool) {
            if (test == leadingTest) {
                // do not re-select the leading tests
                continue;
            }
            if (!mInstanceListerner.isPendingTestInstance(test, leadingTestConfig)) {
                // select only compatible
                continue;
            }
            if (getTestInstabilityRating(test) != leadingInstability) {
                // pack along only cases in the same stability category. Packing more dangerous
                // tests along jeopardizes the stability of this run. Packing more stable tests
                // along jeopardizes their stability rating.
                continue;
            }
            if (runBatch.tests.size() >= getBatchSizeLimitForInstability(leadingInstability)) {
                // batch size is limited.
                break;
            }
            runBatch.tests.add(test);
        }

        return runBatch;
    }

    private int getBatchNumPendingCases(TestBatch batch) {
        int numPending = 0;
        for (TestIdentifier test : batch.tests) {
            if (mInstanceListerner.isPendingTestInstance(test, batch.config)) {
                ++numPending;
            }
        }
        return numPending;
    }

    private int getBatchSizeLimitForInstability(int batchInstabilityRating) {
        // reduce group size exponentially down to one
        return Math.max(1, TESTCASE_BATCH_LIMIT / (1 << batchInstabilityRating));
    }

    private int getTestInstabilityRating(TestIdentifier testId) {
        if (mTestInstabilityRatings.containsKey(testId)) {
            return mTestInstabilityRatings.get(testId);
        } else {
            return 0;
        }
    }

    private void recordTestInstability(TestIdentifier testId) {
        mTestInstabilityRatings.put(testId, getTestInstabilityRating(testId) + 1);
    }

    private void clearTestInstability(TestIdentifier testId) {
        mTestInstabilityRatings.put(testId, 0);
    }

    /**
     * Executes all tests on the device.
     */
    private void runTests() throws DeviceNotAvailableException, CapabilityQueryFailureException {
        for (;;) {
            TestBatch batch = selectRunBatch();

            if (batch == null) {
                break;
            }

            runTestRunBatch(batch);
        }
    }

    /**
     * Runs a TestBatch by either faking it or executing it on a device.
     */
    private void runTestRunBatch(TestBatch batch) throws DeviceNotAvailableException,
            CapabilityQueryFailureException {
        // prepare instance listener
        mInstanceListerner.setCurrentConfig(batch.config);
        for (TestIdentifier test : batch.tests) {
            mInstanceListerner.setTestInstances(test, getTestRunConfigs(test));
        }

        // execute only if config is executable, else fake results
        if (isSupportedRunConfiguration(batch.config)) {
            executeTestRunBatch(batch);
        } else {
            fakePassTestRunBatch(batch);
        }
    }

    private boolean isSupportedRunConfiguration(BatchRunConfiguration runConfig)
            throws DeviceNotAvailableException, CapabilityQueryFailureException {
        // orientation support
        if (!BatchRunConfiguration.ROTATION_UNSPECIFIED.equals(runConfig.getRotation())) {
            final Set<String> features = getDeviceFeatures(mDevice);

            if (isPortraitClassRotation(runConfig.getRotation()) &&
                    !features.contains(FEATURE_PORTRAIT)) {
                return false;
            }
            if (isLandscapeClassRotation(runConfig.getRotation()) &&
                    !features.contains(FEATURE_LANDSCAPE)) {
                return false;
            }
        }

        if (isOpenGlEsPackage()) {
            // renderability support for OpenGL ES tests
            return isSupportedGlesRenderConfig(runConfig);
        } else {
            return true;
        }
    }

    private static final class AdbComLinkOpenError extends Exception {
        public AdbComLinkOpenError(String description, Throwable inner) {
            super(description, inner);
        }
    };
    private static final class AdbComLinkKilledError extends Exception {
        public AdbComLinkKilledError(String description, Throwable inner) {
            super(description, inner);
        }
    };

    /**
     * Executes a given command in adb shell
     *
     * @throws AdbComLinkOpenError if connection cannot be established.
     * @throws AdbComLinkKilledError if established connection is killed prematurely.
     */
    private void executeShellCommandAndReadOutput(final String command,
            final IShellOutputReceiver receiver)
            throws AdbComLinkOpenError, AdbComLinkKilledError {
        try {
            mDevice.getIDevice().executeShellCommand(command, receiver,
                    UNRESPOSIVE_CMD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            // Opening connection timed out
            throw new AdbComLinkOpenError("opening connection timed out", ex);
        } catch (AdbCommandRejectedException ex) {
            // Command rejected
            throw new AdbComLinkOpenError("command rejected", ex);
        } catch (IOException ex) {
            // shell command channel killed
            throw new AdbComLinkKilledError("command link killed", ex);
        } catch (ShellCommandUnresponsiveException ex) {
            // shell command halted
            throw new AdbComLinkKilledError("command link hung", ex);
        }
    }

    /**
     * Executes given test batch on a device
     */
    private void executeTestRunBatch(TestBatch batch) throws DeviceNotAvailableException {
        // attempt full run once
        executeTestRunBatchRun(batch);

        // split remaining tests to two sub batches and execute both. This will terminate
        // since executeTestRunBatchRun will always progress for a batch of size 1.
        final ArrayList<TestIdentifier> pendingTests = new ArrayList<>();

        for (TestIdentifier test : batch.tests) {
            if (mInstanceListerner.isPendingTestInstance(test, batch.config)) {
                pendingTests.add(test);
            }
        }

        final int divisorNdx = pendingTests.size() / 2;
        final List<TestIdentifier> headList = pendingTests.subList(0, divisorNdx);
        final List<TestIdentifier> tailList = pendingTests.subList(divisorNdx, pendingTests.size());

        // head
        for (;;) {
            TestBatch subBatch = selectRunBatch(headList, batch.config);

            if (subBatch == null) {
                break;
            }

            executeTestRunBatch(subBatch);
        }

        // tail
        for (;;) {
            TestBatch subBatch = selectRunBatch(tailList, batch.config);

            if (subBatch == null) {
                break;
            }

            executeTestRunBatch(subBatch);
        }

        if (getBatchNumPendingCases(batch) != 0) {
            throw new AssertionError("executeTestRunBatch postcondition failed");
        }
    }

    private static final class FileRecreateFailedException extends Exception {
    };

    /**
     * Creates a log file suitable for writing.
     *
     * Creates an empty file that is suitable for writing. If target file already exists, it will
     * first be removed and then recreated. This delete-recreate cycle should guarantee that the
     * target file is always writable and is not affected by file-access flags of the pre-existing
     * file.
     *
     * Precreating a file in the runner makes detecting file-access issues faster and more
     * reliable. It also avoids issues caused by sdcard FUSE spuriously preventing file creation.
     */
    private void precreateLogFile() throws DeviceNotAvailableException,
            FileRecreateFailedException {
        final int NUM_ATTEMPTS = 4;
        int attemptNum = 0;
        for (;;) {
            mDevice.executeShellCommand("rm " + LOG_FILE_NAME);
            ++attemptNum;

            if (!mDevice.doesFileExist(LOG_FILE_NAME)) {
                // yay, remove works like it should
                break;
            } else if (attemptNum < NUM_ATTEMPTS) {
                // wait if we failed
                CLog.w("Remote file removal failed, retrying...");
                mSleepProvider.sleep(FILE_REMOVE_RETRY_INTERVAL);
            } else {
                // Bail
                CLog.e("Could not delete a remote file.");
                throw new FileRecreateFailedException();
            }
        }

        // create & truncate and make world read-writable
        mDevice.pushString("", LOG_FILE_NAME);

        if (!mDevice.doesFileExist(LOG_FILE_NAME)) {
            CLog.e("Could not precreate log file.");
            throw new FileRecreateFailedException();
        }
    }

    /**
     * Runs one execution pass over the given batch.
     *
     * Tries to run the batch. Always makes progress (executes instances or modifies stability
     * scores).
     */
    private void executeTestRunBatchRun(TestBatch batch) throws DeviceNotAvailableException {
        if (getBatchNumPendingCases(batch) != batch.tests.size()) {
            throw new AssertionError("executeTestRunBatchRun precondition failed");
        }

        checkInterrupted(); // throws if interrupted

        final String testCases = generateTestCaseTrie(batch.tests);

        // Caselist file cannot linger.
        mDevice.executeShellCommand("rm " + CASE_LIST_FILE_NAME);
        mDevice.pushString(testCases + "\n", CASE_LIST_FILE_NAME);

        final String instrumentationName =
                "com.drawelements.deqp/com.drawelements.deqp.testercore.DeqpInstrumentation";

        final StringBuilder deqpCmdLine = new StringBuilder();
        deqpCmdLine.append("--deqp-caselist-file=");
        deqpCmdLine.append(CASE_LIST_FILE_NAME);
        deqpCmdLine.append(" ");
        deqpCmdLine.append(getRunConfigDisplayCmdLine(batch.config));

        // If we are not logging data, do not bother outputting the images from the test exe.
        if (!mLogData) {
            deqpCmdLine.append(" --deqp-log-images=disable");
        }

        deqpCmdLine.append(" --deqp-watchdog=enable");

        final String command = String.format(
                "am instrument %s -w -e deqpLogFileName \"%s\" -e deqpCmdLine \"%s\""
                    + " -e deqpLogData \"%s\" %s",
                AbiUtils.createAbiFlag(mAbi.getName()), LOG_FILE_NAME, deqpCmdLine.toString(),
                mLogData, instrumentationName);

        final int numRemainingInstancesBefore = getNumRemainingInstances();
        final InstrumentationParser parser = new InstrumentationParser(mInstanceListerner);
        Throwable interruptingError = null;

        try {
            // we might have lingering log file from a previous dirty run and it might be somehow
            // locked (details unknown). Make sure it's writable when test is run.
            precreateLogFile();
            executeShellCommandAndReadOutput(command, parser);
        } catch (Throwable ex) {
            interruptingError = ex;
        } finally {
            parser.flush();
        }

        final boolean progressedSinceLastCall = mInstanceListerner.getCurrentTestId() != null ||
                getNumRemainingInstances() < numRemainingInstancesBefore;

        if (progressedSinceLastCall) {
            mDeviceRecovery.onExecutionProgressed();
        }

        // interrupted, try to recover
        if (interruptingError != null) {
            if (interruptingError instanceof AdbComLinkOpenError) {
                mDeviceRecovery.recoverConnectionRefused();
            } else if (interruptingError instanceof AdbComLinkKilledError) {
                mDeviceRecovery.recoverCommandNotCompleted();
            } else if (interruptingError instanceof FileRecreateFailedException) {
                mDeviceRecovery.recoverCommandNotCompleted();
            } else if (interruptingError instanceof RunInterruptedException) {
                // external run interruption request. Terminate immediately.
                throw (RunInterruptedException)interruptingError;
            } else {
                CLog.e(interruptingError);
                throw new RuntimeException(interruptingError);
            }

            // recoverXXX did not throw => recovery succeeded
        } else if (!parser.wasSuccessful()) {
            mDeviceRecovery.recoverCommandNotCompleted();
            // recoverXXX did not throw => recovery succeeded
        }

        // Progress guarantees.
        if (batch.tests.size() == 1) {
            final TestIdentifier onlyTest = batch.tests.iterator().next();
            final boolean wasTestExecuted =
                    !mInstanceListerner.isPendingTestInstance(onlyTest, batch.config) &&
                    mInstanceListerner.getCurrentTestId() == null;
            final boolean wasLinkFailure = !parser.wasSuccessful() || interruptingError != null;

            // Link failures can be caused by external events, require at least two observations
            // until bailing.
            if (!wasTestExecuted && (!wasLinkFailure || getTestInstabilityRating(onlyTest) > 0)) {
                recordTestInstability(onlyTest);
                // If we cannot finish the test, mark the case as a crash.
                //
                // If we couldn't even start the test, fail the test instance as non-executable.
                // This is required so that a consistently crashing or non-existent tests will
                // not cause futile (non-terminating) re-execution attempts.
                if (mInstanceListerner.getCurrentTestId() != null) {
                    mInstanceListerner.abortTest(onlyTest, INCOMPLETE_LOG_MESSAGE);
                } else {
                    mInstanceListerner.abortTest(onlyTest, NOT_EXECUTABLE_LOG_MESSAGE);
                }
            } else if (wasTestExecuted) {
                clearTestInstability(onlyTest);
            }
        }
        else
        {
            // Analyze results to update test stability ratings. If there is no interrupting test
            // logged, increase instability rating of all remaining tests. If there is a
            // interrupting test logged, increase only its instability rating.
            //
            // A successful run of tests clears instability rating.
            if (mInstanceListerner.getCurrentTestId() == null) {
                for (TestIdentifier test : batch.tests) {
                    if (mInstanceListerner.isPendingTestInstance(test, batch.config)) {
                        recordTestInstability(test);
                    } else {
                        clearTestInstability(test);
                    }
                }
            } else {
                recordTestInstability(mInstanceListerner.getCurrentTestId());
                for (TestIdentifier test : batch.tests) {
                    // \note: isPendingTestInstance is false for getCurrentTestId. Current ID is
                    // considered 'running' and will be restored to 'pending' in endBatch().
                    if (!test.equals(mInstanceListerner.getCurrentTestId()) &&
                            !mInstanceListerner.isPendingTestInstance(test, batch.config)) {
                        clearTestInstability(test);
                    }
                }
            }
        }

        mInstanceListerner.endBatch();
    }

    private static String getRunConfigDisplayCmdLine(BatchRunConfiguration runConfig) {
        final StringBuilder deqpCmdLine = new StringBuilder();
        if (!runConfig.getGlConfig().isEmpty()) {
            deqpCmdLine.append("--deqp-gl-config-name=");
            deqpCmdLine.append(runConfig.getGlConfig());
        }
        if (!runConfig.getRotation().isEmpty()) {
            if (deqpCmdLine.length() != 0) {
                deqpCmdLine.append(" ");
            }
            deqpCmdLine.append("--deqp-screen-rotation=");
            deqpCmdLine.append(runConfig.getRotation());
        }
        if (!runConfig.getSurfaceType().isEmpty()) {
            if (deqpCmdLine.length() != 0) {
                deqpCmdLine.append(" ");
            }
            deqpCmdLine.append("--deqp-surface-type=");
            deqpCmdLine.append(runConfig.getSurfaceType());
        }
        return deqpCmdLine.toString();
    }

    private int getNumRemainingInstances() {
        int retVal = 0;
        for (TestIdentifier testId : mRemainingTests) {
            // If case is in current working set, sum only not yet executed instances.
            // If case is not in current working set, sum all instances (since they are not yet
            // executed).
            if (mInstanceListerner.mPendingResults.containsKey(testId)) {
                retVal += mInstanceListerner.mPendingResults.get(testId).remainingConfigs.size();
            } else {
                retVal += mTestInstances.get(testId).size();
            }
        }
        return retVal;
    }

    /**
     * Checks if this execution has been marked as interrupted and throws if it has.
     */
    private void checkInterrupted() throws RunInterruptedException {
        // Work around the API. RunUtil::checkInterrupted is private but we can call it indirectly
        // by sleeping a value <= 0.
        mRunUtil.sleep(0);
    }

    /**
     * Pass given batch tests without running it
     */
    private void fakePassTestRunBatch(TestBatch batch) {
        for (TestIdentifier test : batch.tests) {
            CLog.d("Skipping test '%s' invocation in config '%s'", test.toString(),
                    batch.config.getId());
            mInstanceListerner.skipTest(test);
        }
    }

    /**
     * Pass all remaining tests without running them
     */
    private void fakePassTests(ITestInvocationListener listener) {
        Map <String, String> emptyMap = Collections.emptyMap();
        for (TestIdentifier test : mRemainingTests) {
            CLog.d("Skipping test '%s', Opengl ES version not supported", test.toString());
            listener.testStarted(test);
            listener.testEnded(test, emptyMap);
        }
        mRemainingTests.clear();
    }

    /**
     * Check if device supports OpenGL ES version.
     */
    private static boolean isSupportedGles(ITestDevice device, int requiredMajorVersion,
            int requiredMinorVersion) throws DeviceNotAvailableException {
        String roOpenglesVersion = device.getProperty("ro.opengles.version");

        if (roOpenglesVersion == null)
            return false;

        int intValue = Integer.parseInt(roOpenglesVersion);

        int majorVersion = ((intValue & 0xffff0000) >> 16);
        int minorVersion = (intValue & 0xffff);

        return (majorVersion > requiredMajorVersion)
                || (majorVersion == requiredMajorVersion && minorVersion >= requiredMinorVersion);
    }

    /**
     * Query if rendertarget is supported
     */
    private boolean isSupportedGlesRenderConfig(BatchRunConfiguration runConfig)
            throws DeviceNotAvailableException, CapabilityQueryFailureException {
        // query if configuration is supported
        final StringBuilder configCommandLine =
                new StringBuilder(getRunConfigDisplayCmdLine(runConfig));
        if (configCommandLine.length() != 0) {
            configCommandLine.append(" ");
        }
        configCommandLine.append("--deqp-gl-major-version=");
        configCommandLine.append(getGlesMajorVersion());
        configCommandLine.append(" --deqp-gl-minor-version=");
        configCommandLine.append(getGlesMinorVersion());

        final String commandLine = configCommandLine.toString();

        // check for cached result first
        if (mConfigQuerySupportCache.containsKey(commandLine)) {
            return mConfigQuerySupportCache.get(commandLine);
        }

        final boolean supported = queryIsSupportedConfigCommandLine(commandLine);
        mConfigQuerySupportCache.put(commandLine, supported);
        return supported;
    }

    private boolean queryIsSupportedConfigCommandLine(String deqpCommandLine)
            throws DeviceNotAvailableException, CapabilityQueryFailureException {
        final String instrumentationName =
                "com.drawelements.deqp/com.drawelements.deqp.platformutil.DeqpPlatformCapabilityQueryInstrumentation";
        final String command = String.format(
                "am instrument %s -w -e deqpQueryType renderConfigSupported -e deqpCmdLine \"%s\""
                    + " %s",
                AbiUtils.createAbiFlag(mAbi.getName()), deqpCommandLine, instrumentationName);

        final PlatformQueryInstrumentationParser parser = new PlatformQueryInstrumentationParser();
        mDevice.executeShellCommand(command, parser);
        parser.flush();

        if (parser.wasSuccessful() && parser.getResultCode() == 0 &&
                parser.getResultMap().containsKey("Supported")) {
            if ("Yes".equals(parser.getResultMap().get("Supported"))) {
                return true;
            } else if ("No".equals(parser.getResultMap().get("Supported"))) {
                return false;
            } else {
                CLog.e("Capability query did not return a result");
                throw new CapabilityQueryFailureException();
            }
        } else if (parser.wasSuccessful()) {
            CLog.e("Failed to run capability query. Code: %d, Result: %s",
                    parser.getResultCode(), parser.getResultMap().toString());
            throw new CapabilityQueryFailureException();
        } else {
            CLog.e("Failed to run capability query");
            throw new CapabilityQueryFailureException();
        }
    }

    /**
     * Return feature set supported by the device
     */
    private Set<String> getDeviceFeatures(ITestDevice device)
            throws DeviceNotAvailableException, CapabilityQueryFailureException {
        if (mDeviceFeatures == null) {
            mDeviceFeatures = queryDeviceFeatures(device);
        }
        return mDeviceFeatures;
    }

    /**
     * Query feature set supported by the device
     */
    private static Set<String> queryDeviceFeatures(ITestDevice device)
            throws DeviceNotAvailableException, CapabilityQueryFailureException {
        // NOTE: Almost identical code in BaseDevicePolicyTest#hasDeviceFeatures
        // TODO: Move this logic to ITestDevice.
        String command = "pm list features";
        String commandOutput = device.executeShellCommand(command);

        // Extract the id of the new user.
        HashSet<String> availableFeatures = new HashSet<>();
        for (String feature: commandOutput.split("\\s+")) {
            // Each line in the output of the command has the format "feature:{FEATURE_VALUE}".
            String[] tokens = feature.split(":");
            if (tokens.length < 2 || !"feature".equals(tokens[0])) {
                CLog.e("Failed parse features. Unexpect format on line \"%s\"", tokens[0]);
                throw new CapabilityQueryFailureException();
            }
            availableFeatures.add(tokens[1]);
        }
        return availableFeatures;
    }

    private boolean isPortraitClassRotation(String rotation) {
        return BatchRunConfiguration.ROTATION_PORTRAIT.equals(rotation) ||
                BatchRunConfiguration.ROTATION_REVERSE_PORTRAIT.equals(rotation);
    }

    private boolean isLandscapeClassRotation(String rotation) {
        return BatchRunConfiguration.ROTATION_LANDSCAPE.equals(rotation) ||
                BatchRunConfiguration.ROTATION_REVERSE_LANDSCAPE.equals(rotation);
    }

    /**
     * Install dEQP OnDevice Package
     */
    private void installTestApk() throws DeviceNotAvailableException {
        try {
            File apkFile = mCtsBuild.getTestApp(DEQP_ONDEVICE_APK);
            String[] options = {AbiUtils.createAbiFlag(mAbi.getName())};
            String errorCode = getDevice().installPackage(apkFile, true, options);
            if (errorCode != null) {
                CLog.e("Failed to install %s. Reason: %s", DEQP_ONDEVICE_APK, errorCode);
            }
        } catch (FileNotFoundException e) {
            CLog.e("Could not find test apk %s", DEQP_ONDEVICE_APK);
        }
    }

    /**
     * Uninstall dEQP OnDevice Package
     */
    private void uninstallTestApk() throws DeviceNotAvailableException {
        getDevice().uninstallPackage(DEQP_ONDEVICE_PKG);
    }

    /**
     * Parse gl nature from package name
     */
    private boolean isOpenGlEsPackage() {
        if ("dEQP-GLES2".equals(mName) || "dEQP-GLES3".equals(mName) ||
                "dEQP-GLES31".equals(mName)) {
            return true;
        } else if ("dEQP-EGL".equals(mName)) {
            return false;
        } else {
            throw new IllegalStateException("dEQP runner was created with illegal name");
        }
    }

    /**
     * Check GL support (based on package name)
     */
    private boolean isSupportedGles() throws DeviceNotAvailableException {
        return isSupportedGles(mDevice, getGlesMajorVersion(), getGlesMinorVersion());
    }

    /**
     * Get GL major version (based on package name)
     */
    private int getGlesMajorVersion() throws DeviceNotAvailableException {
        if ("dEQP-GLES2".equals(mName)) {
            return 2;
        } else if ("dEQP-GLES3".equals(mName)) {
            return 3;
        } else if ("dEQP-GLES31".equals(mName)) {
            return 3;
        } else {
            throw new IllegalStateException("getGlesMajorVersion called for non gles pkg");
        }
    }

    /**
     * Get GL minor version (based on package name)
     */
    private int getGlesMinorVersion() throws DeviceNotAvailableException {
        if ("dEQP-GLES2".equals(mName)) {
            return 0;
        } else if ("dEQP-GLES3".equals(mName)) {
            return 0;
        } else if ("dEQP-GLES31".equals(mName)) {
            return 1;
        } else {
            throw new IllegalStateException("getGlesMinorVersion called for non gles pkg");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        final Map<String, String> emptyMap = Collections.emptyMap();
        final boolean isSupportedApi = !isOpenGlEsPackage() || isSupportedGles();

        listener.testRunStarted(getId(), mRemainingTests.size());

        try {
            if (isSupportedApi) {
                // Make sure there is no pre-existing package form earlier interrupted test run.
                uninstallTestApk();
                installTestApk();

                mInstanceListerner.setSink(listener);
                mDeviceRecovery.setDevice(mDevice);
                runTests();

                uninstallTestApk();
            } else {
                // Pass all tests if OpenGL ES version is not supported
                fakePassTests(listener);
            }
        } catch (CapabilityQueryFailureException ex) {
            // Platform is not behaving correctly, for example crashing when trying to create
            // a window. Instead of silenty failing, signal failure by leaving the rest of the
            // test cases in "NotExecuted" state
            uninstallTestApk();
        }

        listener.testRunEnded(0, emptyMap);
    }
}
