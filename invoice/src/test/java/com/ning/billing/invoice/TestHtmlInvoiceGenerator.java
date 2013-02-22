/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.invoice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.skife.config.ConfigurationObjectFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.formatters.InvoiceFormatterFactory;
import com.ning.billing.invoice.template.HtmlInvoiceGenerator;
import com.ning.billing.invoice.template.formatters.DefaultInvoiceFormatterFactory;
import com.ning.billing.util.email.templates.MustacheTemplateEngine;
import com.ning.billing.util.email.templates.TemplateEngine;
import com.ning.billing.util.template.translation.TranslatorConfig;

public class TestHtmlInvoiceGenerator extends InvoiceTestSuiteNoDB {

    private HtmlInvoiceGenerator g;

    @Override
    @BeforeClass(groups = "fast")
    public void beforeClass() throws Exception {
        super.beforeClass();
        final TranslatorConfig config = new ConfigurationObjectFactory(System.getProperties()).build(TranslatorConfig.class);
        final TemplateEngine templateEngine = new MustacheTemplateEngine();
        final InvoiceFormatterFactory factory = new DefaultInvoiceFormatterFactory();
        g = new HtmlInvoiceGenerator(factory, templateEngine, config);
    }

    @Test(groups = "fast")
    public void testGenerateInvoice() throws Exception {
        final String output = g.generateInvoice(createAccount(), createInvoice(), false);
        Assert.assertNotNull(output);
    }

    @Test(groups = "fast")
    public void testGenerateEmptyInvoice() throws Exception {
        final Invoice invoice = Mockito.mock(Invoice.class);
        final String output = g.generateInvoice(createAccount(), invoice, false);
        Assert.assertNotNull(output);
    }

    @Test(groups = "fast")
    public void testGenerateNullInvoice() throws Exception {
        final String output = g.generateInvoice(createAccount(), null, false);
        Assert.assertNull(output);
    }

    private Account createAccount() {
        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getExternalKey()).thenReturn("1234abcd");
        Mockito.when(account.getName()).thenReturn("Jim Smith");
        Mockito.when(account.getFirstNameLength()).thenReturn(3);
        Mockito.when(account.getEmail()).thenReturn("jim.smith@mail.com");
        Mockito.when(account.getLocale()).thenReturn(Locale.US.toString());
        Mockito.when(account.getAddress1()).thenReturn("123 Some Street");
        Mockito.when(account.getAddress2()).thenReturn("Apt 456");
        Mockito.when(account.getCity()).thenReturn("Some City");
        Mockito.when(account.getStateOrProvince()).thenReturn("Some State");
        Mockito.when(account.getPostalCode()).thenReturn("12345-6789");
        Mockito.when(account.getCountry()).thenReturn("USA");
        Mockito.when(account.getPhone()).thenReturn("123-456-7890");

        return account;
    }

    private Invoice createInvoice() {
        final LocalDate startDate = new LocalDate(new DateTime().minusMonths(1), DateTimeZone.UTC);
        final LocalDate endDate = new LocalDate(DateTimeZone.UTC);

        final BigDecimal price1 = new BigDecimal("29.95");
        final BigDecimal price2 = new BigDecimal("59.95");
        final Invoice dummyInvoice = Mockito.mock(Invoice.class);
        Mockito.when(dummyInvoice.getInvoiceDate()).thenReturn(startDate);
        Mockito.when(dummyInvoice.getInvoiceNumber()).thenReturn(42);
        Mockito.when(dummyInvoice.getCurrency()).thenReturn(Currency.USD);
        Mockito.when(dummyInvoice.getChargedAmount()).thenReturn(price1.add(price2));
        Mockito.when(dummyInvoice.getPaidAmount()).thenReturn(BigDecimal.ZERO);
        Mockito.when(dummyInvoice.getBalance()).thenReturn(price1.add(price2));

        final List<InvoiceItem> items = new ArrayList<InvoiceItem>();
        items.add(createInvoiceItem(price1, "Domain 1", startDate, endDate, "ning-plus"));
        items.add(createInvoiceItem(price2, "Domain 2", startDate, endDate, "ning-pro"));
        Mockito.when(dummyInvoice.getInvoiceItems()).thenReturn(items);

        return dummyInvoice;
    }

    private InvoiceItem createInvoiceItem(final BigDecimal amount, final String networkName, final LocalDate startDate,
                                          final LocalDate endDate, final String planName) {
        final InvoiceItem item = Mockito.mock(InvoiceItem.class);
        Mockito.when(item.getAmount()).thenReturn(amount);
        Mockito.when(item.getStartDate()).thenReturn(startDate);
        Mockito.when(item.getEndDate()).thenReturn(endDate);
        Mockito.when(item.getPlanName()).thenReturn(planName);
        Mockito.when(item.getDescription()).thenReturn(networkName);

        return item;
    }
}
