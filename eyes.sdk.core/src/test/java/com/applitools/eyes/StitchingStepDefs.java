package com.applitools.eyes;

import cucumber.api.DataTable;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.testng.Assert;

import java.util.List;

/**
 * Step definitions for the Stitching feature.
 */
@SuppressWarnings({"unused", "SpellCheckingInspection"})
public class StitchingStepDefs {

    private RegionF regionToDivide = null;
    private Iterable<RegionF> subRegions = null;
    private boolean illegalArgumentExceptionThrown = false;

    @Given("^I have a region with left (\\d+) and top (\\d+) and width (\\d+) and height (\\d+)$")
    public void I_have_a_region_with_width_and_height(int left, int top,
                                                      int width, int height) {
        regionToDivide = new RegionF(left, top, width, height);
    }

    private void divideIntoSubRegions(int width, int height, boolean isFixedSize) {
        try {
            subRegions = regionToDivide.getSubRegions(
                    new RectangleSizeF(width, height), isFixedSize);
        } catch (IllegalArgumentException e) {
            illegalArgumentExceptionThrown = true;
        }
    }

    @When("^I divide the region into fixed-size sub regions with width (\\d+) and height (\\d+)$")
    public void I_divide_the_region_into_fixed_size_sub_regions_with_width_and_height(int width, int height) {
        divideIntoSubRegions(width, height, true);
    }

    @When("^I divide the region into varying-size sub regions with width (\\d+) and height (\\d+)$")
    public void I_divide_the_region_into_varying_size_sub_regions_with_width_and_height(int width, int height) {
        divideIntoSubRegions(width, height, false);
    }

    @When("^I divide the region into sub regions with width (\\d+) and height (\\d+) without specifying sub-region type$")
    public void I_divide_the_region_into_sub_regions_with_width_and_height_without_specifying_sub_region_type(int width, int height) {
        subRegions = regionToDivide.getSubRegions(new RectangleSizeF(width, height));
    }

    @Then("^I get the following sub-regions:$")
    public void I_get_the_following_sub_regions(DataTable validSubRegionsDT) {
        List<RegionF> validSubRegions = validSubRegionsDT.asList(RegionF.class);

        int subRegionsSize = 0;
        for (RegionF currentSubRegion : subRegions) {
            Assert.assertTrue(validSubRegions.contains(currentSubRegion),"Invalid Sub region: " + currentSubRegion);
            ++subRegionsSize;
        }

        Assert.assertEquals(subRegionsSize, validSubRegions.size(),"Number of sub-regions");
    }

    @Then("^An exception should be thrown.$")
    public void An_exception_should_be_thrown() {
        Assert.assertTrue(illegalArgumentExceptionThrown);
    }
}
