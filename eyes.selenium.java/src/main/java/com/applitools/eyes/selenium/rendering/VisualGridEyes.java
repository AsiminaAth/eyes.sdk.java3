package com.applitools.eyes.selenium.rendering;

import com.applitools.ICheckSettings;
import com.applitools.eyes.*;
import com.applitools.eyes.visualGridClient.model.*;
import com.applitools.eyes.visualGridClient.services.*;
import com.applitools.utils.ArgumentGuard;
import com.applitools.utils.GeneralUtils;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class VisualGridEyes implements IRenderingEyes {

    private Logger logger;

    private String apiKey;
    private String serverUrl;

    private final VisualGridRunner renderingGridManager;
    private List<RunningTest> testList = Collections.synchronizedList(new ArrayList<RunningTest>());
    private final List<RunningTest> testsInCloseProcess = Collections.synchronizedList(new ArrayList<RunningTest>());
    private AtomicBoolean isVGEyesClosed = new AtomicBoolean(false);
    private AtomicBoolean isVGEyesIssuedOpenTasks = new AtomicBoolean(false);
    private IRenderingEyes.EyesListener listener;
    private AbstractProxySettings proxy;

    private String PROCESS_RESOURCES;
    private JavascriptExecutor jsExecutor;
    private RenderingInfo renderingInfo;
    private IEyesConnector VGEyesConnector;
    private BatchInfo batchInfo = new BatchInfo(null);

    private IDebugResourceWriter debugResourceWriter;
    private String url;
    private List<Future<TestResultContainer>> futures = null;
    private String branchName = null;
    private String parentBranchName = null;
    private boolean hideCaret = false;
    private Boolean isDisabled;
    private MatchLevel matchLevel = MatchLevel.STRICT;

    {
        try {
            PROCESS_RESOURCES = GeneralUtils.readToEnd(VisualGridEyes.class.getResourceAsStream("/processPageAndSerialize.js"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private RunningTest.RunningTestListener testListener = new RunningTest.RunningTestListener() {
        @Override
        public void onTaskComplete(Task task, RunningTest test) {
            switch (task.getType()) {
                case CLOSE:
                case ABORT:
                    boolean isVGEyesClosed = true;
                    for (RunningTest runningTest : testList) {
                        isVGEyesClosed &= runningTest.isTestClose();
                    }
                    VisualGridEyes.this.isVGEyesClosed.set(isVGEyesClosed);
                    break;
                case OPEN:

            }

            if (VisualGridEyes.this.listener != null) {
                VisualGridEyes.this.listener.onTaskComplete(task, VisualGridEyes.this);
            }
        }

        @Override
        public void onRenderComplete() {
            logger.verbose("enter");
            VisualGridEyes.this.listener.onRenderComplete();
            logger.verbose("exit");
        }
    };

    public VisualGridEyes(VisualGridRunner renderingGridManager) {
        ArgumentGuard.notNull(renderingGridManager, "renderingGridManager");
        this.renderingGridManager = renderingGridManager;
        this.logger = renderingGridManager.getLogger();
    }

    /**
     * Sets a handler of log messages generated by this API.
     *
     * @param logHandler Handles log messages generated by this API.
     */
    public void setLogHandler(LogHandler logHandler) {
        if (getIsDisabled()) return;
        LogHandler currentLogHandler = logger.getLogHandler();
        this.logger = new Logger();
        this.logger.setLogHandler(new MultiLogHandler(currentLogHandler, logHandler));

        if (currentLogHandler.isOpen() && !logHandler.isOpen()) {
            logHandler.open();
        }
    }

    public void open(WebDriver webDriver, RenderingConfiguration renderingConfiguration) {
        if (getIsDisabled()) return;
        logger.verbose("enter");

        ArgumentGuard.notNull(webDriver, "webDriver");
        ArgumentGuard.notNull(renderingConfiguration, "renderingConfiguration");

        initDriver(webDriver);

        if (renderingConfiguration.getBatch() == null) {
            renderingConfiguration.setBatch(batchInfo);
        }

        logger.verbose("getting all browsers info...");
        List<RenderBrowserInfo> browserInfoList = renderingConfiguration.getBrowsersInfo();
        logger.verbose("creating test descriptors for each browser info...");
        for (RenderBrowserInfo browserInfo : browserInfoList) {
            logger.verbose("creating test descriptor");
            RunningTest test = new RunningTest(createVGEyesConnector(browserInfo), renderingConfiguration, browserInfo, logger, testListener);
            this.testList.add(test);
        }

        logger.verbose(String.format("opening %d tests...", testList.size()));
        this.renderingGridManager.open(this, renderingInfo);
        logger.verbose("done");
    }

    private IEyesConnector createVGEyesConnector(RenderBrowserInfo browserInfo) {
        logger.verbose("creating VisualGridEyes server connector");
        IEyesConnector VGEyesConnector = new EyesConnector(browserInfo, renderingGridManager.getRateLimiter());
        if (browserInfo.getEmulationInfo() != null) {
            VGEyesConnector.setDevice(browserInfo.getEmulationInfo().getDeviceName());
        }
        VGEyesConnector.setLogHandler(this.logger.getLogHandler());
        VGEyesConnector.setProxy(this.proxy);
        VGEyesConnector.setBatch(batchInfo);
        VGEyesConnector.setBranchName(this.branchName);
        VGEyesConnector.setParentBranchName(parentBranchName);
        VGEyesConnector.setHideCaret(this.hideCaret);
        VGEyesConnector.setMatchLevel(matchLevel);

        URI serverUri = this.getServerUrl();
        if (serverUri != null) {
            try {
                VGEyesConnector.setServerUrl(serverUri.toString());
            } catch (URISyntaxException e) {
                GeneralUtils.logExceptionStackTrace(logger, e);
            }
        }

        String apiKey = this.getApiKey();
        if (apiKey != null) {
            VGEyesConnector.setApiKey(apiKey);
        } else {
            throw new EyesException("Missing API key");
        }

        if (this.renderingInfo == null) {
            logger.verbose("initializing rendering info...");
            this.renderingInfo = VGEyesConnector.getRenderingInfo();
        }
        VGEyesConnector.setRenderInfo(this.renderingInfo);

        this.VGEyesConnector = VGEyesConnector;
        return VGEyesConnector;
    }

    private void initDriver(WebDriver webDriver) {
        if (webDriver instanceof JavascriptExecutor) {
            this.jsExecutor = (JavascriptExecutor) webDriver;
        }
        String currentUrl = webDriver.getCurrentUrl();
        this.url = currentUrl;
    }

    public RunningTest getNextTestToClose() {
        synchronized (testsInCloseProcess) {
            for (RunningTest runningTest : testList) {
                if (!runningTest.isTestClose() && runningTest.isTestReadyToClose() && !this.testsInCloseProcess.contains(runningTest)) {
                    this.testsInCloseProcess.add(runningTest);
                    return runningTest;
                }
            }
        }
        return null;
    }

    public List<Future<TestResultContainer>> close() {
        if (getIsDisabled()) return null;
        futures = closeAndReturnResults();
        return futures;
    }

    public List<Future<TestResultContainer>> close(boolean throwException) {
        if (getIsDisabled()) return null;
        futures = closeAndReturnResults();
        return futures;
    }

    public void abortIfNotClosed() {
    }

    public boolean getIsOpen() {
        return !isEyesClosed();
    }

    public String getApiKey() {
        return this.apiKey == null ? this.renderingGridManager.getApiKey() : this.apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setIsDisabled(boolean disabled) {
        this.isDisabled = disabled;
    }

    public boolean getIsDisabled() {
        return this.isDisabled == null ? this.renderingGridManager.getIsDisabled() : this.isDisabled;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public void setParentBranchName(String branchName) {
        this.parentBranchName = branchName;
    }

    public void setHideCaret(boolean hideCaret) {
        this.hideCaret = hideCaret;
    }

    public void setMatchLevel(MatchLevel level) {
        this.matchLevel = level;
    }

    public URI getServerUrl() {
        if (this.VGEyesConnector != null) {
            URI uri = this.VGEyesConnector.getServerUrl();
            if (uri != null) return uri;
        }
        String str = this.serverUrl == null ? this.renderingGridManager.getServerUrl() : this.serverUrl;
        return str == null ? null : URI.create(str);
    }

    private List<Future<TestResultContainer>> closeAndReturnResults() {
        if (getIsDisabled()) return new ArrayList<>();
        if (this.futures != null) {
            return futures;
        }
        List<Future<TestResultContainer>> futureList;
        logger.verbose("enter " + batchInfo);
        futureList = new ArrayList<>();
        try {
            for (RunningTest runningTest : testList) {
                logger.verbose("running test name: " + runningTest.getConfiguration().getTestName());
                logger.verbose("is current running test open: " + runningTest.isTestOpen());
                logger.verbose("is current running test ready to close: " + runningTest.isTestReadyToClose());
                logger.verbose("is current running test closed: " + runningTest.isTestClose());
                if (!runningTest.isTestClose()) {
                    logger.verbose("closing current running test");
                    FutureTask<TestResultContainer> closeFuture = runningTest.close();
                    logger.verbose("adding closeFuture to futureList");
                    futureList.add(closeFuture);
                }
            }
            futures = futureList;
            this.renderingGridManager.close(this);
        } catch (Exception e) {
            GeneralUtils.logExceptionStackTrace(logger, e);
        }
        return futureList;
    }

    @Override
    public synchronized ScoreTask getBestScoreTaskForCheck() {

        int bestScore = -1;

        ScoreTask currentBest = null;
        for (RunningTest runningTest : testList) {

            List<Task> taskList = runningTest.getTaskList();

            Task task;
            synchronized (taskList) {
                if (taskList.isEmpty()) continue;

                task = taskList.get(0);
                if (!runningTest.isTestOpen() || task.getType() != Task.TaskType.CHECK || !task.isTaskReadyToCheck())
                    continue;
            }


            ScoreTask scoreTask = runningTest.getScoreTaskObjectByType(Task.TaskType.CHECK);

            if (scoreTask == null) continue;

            if (bestScore < scoreTask.getScore()) {
                currentBest = scoreTask;
                bestScore = scoreTask.getScore();
            }
        }
        return currentBest;
    }

    @Override
    public ScoreTask getBestScoreTaskForOpen() {
        int bestMark = -1;
        ScoreTask currentBest = null;
        synchronized (testList) {
            for (RunningTest runningTest : testList) {

                ScoreTask currentScoreTask = runningTest.getScoreTaskObjectByType(Task.TaskType.OPEN);
                if (currentScoreTask == null) continue;

                if (bestMark < currentScoreTask.getScore()) {
                    bestMark = currentScoreTask.getScore();
                    currentBest = currentScoreTask;

                }
            }
        }
        return currentBest;
    }

    @Override
    public void setBatch(BatchInfo batchInfo) {
        this.batchInfo = batchInfo;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    @Override
    public boolean isEyesClosed() {
        boolean isVGEyesClosed = true;
        for (RunningTest runningTest : testList) {
            isVGEyesClosed = isVGEyesClosed && runningTest.isTestClose();
        }
        return isVGEyesClosed;
    }

    public void setListener(EyesListener listener) {
        this.listener = listener;
    }

    /**
     * Sets the proxy settings to be used by the rest client.
     *
     * @param abstractProxySettings The proxy settings to be used by the rest client.
     *                              If {@code null} then no proxy is set.
     */
    public void setProxy(AbstractProxySettings abstractProxySettings) {
        this.proxy = abstractProxySettings;
    }

    public void check(String name, ICheckSettings checkSettings) {
        if (getIsDisabled()) return;
        ArgumentGuard.notNull(checkSettings, "checkSettings");
        checkSettings = checkSettings.withName(name);
        this.check(checkSettings);
    }

    public void check(ICheckSettings checkSettings) {
        if (getIsDisabled()) return;
        logger.verbose("enter");

        ArgumentGuard.notOfType(checkSettings, ICheckSettings.class, "checkSettings");

        List<Task> openTasks = addOpenTaskToAllRunningTest();

        List<Task> taskList = new ArrayList<>();

        String domCaptureScript = "var callback = arguments[arguments.length - 1]; return (" + PROCESS_RESOURCES + ")().then(JSON.stringify).then(callback, function(err) {callback(err.stack || err.toString())})";

        logger.verbose(" $$$$$$$$$$    Dom extraction starting   (" + checkSettings.toString() + ")   $$$$$$$$$$$$");
        String scriptResult = (String) this.jsExecutor.executeAsyncScript(domCaptureScript);

        logger.verbose(" $$$$$$$$$$    Dom extracted  (" + checkSettings.toString() + ")   $$$$$$$$$$$$");

        for (final RunningTest test : testList) {
            Task checkTask = test.check(checkSettings);
            taskList.add(checkTask);
        }

        logger.verbose(" $$$$$$$$$$    added check tasks  (" + checkSettings.toString() + ")   $$$$$$$$$$$$");

        this.renderingGridManager.check(checkSettings, debugResourceWriter, scriptResult,
                this.VGEyesConnector, taskList, openTasks,
                new VisualGridRunner.RenderListener() {
                    @Override
                    public void onRenderSuccess() {

                    }

                    @Override
                    public void onRenderFailed(Exception e) {

                    }
                });

        logger.verbose(" $$$$$$$$$$    created renderTask  (" + checkSettings.toString() + ")   $$$$$$$$$$$$");
    }

    private synchronized List<Task> addOpenTaskToAllRunningTest() {
        logger.verbose("enter");
        List<Task> tasks = new ArrayList<>();
        if (!this.isVGEyesIssuedOpenTasks.get()) {
            for (RunningTest runningTest : testList) {
                Task task = runningTest.open();
                tasks.add(task);
            }
            logger.verbose("calling addOpenTaskToAllRunningTest.open");
            this.isVGEyesIssuedOpenTasks.set(true);
        }
        logger.verbose("exit");
        return tasks;
    }

    public Logger getLogger() {
        return logger;
    }

    public List<RunningTest> getAllRunningTests() {
        return testList;
    }

    public void setDebugResourceWriter(IDebugResourceWriter debugResourceWriter) {
        this.debugResourceWriter = debugResourceWriter;
    }

    @Override
    public String toString() {
        return "SelenuimVGEyes - url: " + url;
    }

    public List<Future<TestResultContainer>> getCloseFutures() {
        return futures;
    }
}
