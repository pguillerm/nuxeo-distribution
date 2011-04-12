/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Thierry Delprat
 */

package org.nuxeo.functionaltests.pages.admincenter;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class UpdateCenterPage extends AdminCenterBasePage {

    public UpdateCenterPage(WebDriver driver) {
        super(driver);
    }

    public PackageListingPage getPackageListingPage() {
        driver.switchTo().frame(0);
        PackageListingPage page = asPage(PackageListingPage.class);
        findElementWithTimeout(By.xpath("//table[@class='packageListing']"));
        return page;
    }

}
