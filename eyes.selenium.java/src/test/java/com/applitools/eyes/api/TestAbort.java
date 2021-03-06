package com.applitools.eyes.api;

import com.applitools.eyes.EyesRunner;
import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.selenium.*;
import com.applitools.eyes.selenium.fluent.Target;
import com.applitools.eyes.utils.SeleniumUtils;
import com.applitools.eyes.visualgrid.services.VisualGridRunner;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

public class TestAbort {
    private WebDriver driver;
    private Eyes eyes;
    private EyesRunner runner;
    private boolean useVisualGrid;

    @Factory(dataProvider = "booleanDP", dataProviderClass = TestDataProvider.class)
    public TestAbort(boolean useVisualGrid) {
        this.useVisualGrid = useVisualGrid;
    }

    @BeforeClass
    public void SetUp() {
        driver = SeleniumUtils.createChromeDriver();
        driver.get("data:text/html,<p>Test</p>");
        runner = useVisualGrid ? new VisualGridRunner(10) : new ClassicRunner();
        eyes = new Eyes(runner);
        eyes.setBatch(TestDataProvider.batchInfo);
        String testName = useVisualGrid ? "Test Abort_VG" : "Test Abort";

        Configuration config = eyes.getConfiguration();
        config.addBrowser(800, 600, BrowserType.CHROME);
        eyes.setConfiguration(config);

        eyes.open(driver, testName, testName, new RectangleSize(1200, 800));
    }

    @AfterClass
    public void TearDown() {
        driver.quit();
        runner.getAllTestResults();
    }

    @Test
    public void TestAbortIfNotClosed() {
        eyes.check(useVisualGrid ? "VG" : "SEL", Target.window());
        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        eyes.abortIfNotClosed();
    }
}
