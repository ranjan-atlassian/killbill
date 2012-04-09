/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.account.dao;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.UUID;

import com.ning.billing.util.entity.EntityPersistenceException;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.DefaultAccount;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.tag.DefaultTagDefinition;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;
import com.ning.billing.util.tag.dao.TagDefinitionSqlDao;

@Test(groups = {"slow", "account-dao"})
public class TestSimpleAccountDao extends AccountDaoTestBase {
    private Account createTestAccount(int billCycleDay) {
        return createTestAccount(billCycleDay, "123-456-7890");
    }

    private Account createTestAccount(int billCycleDay, String phone) {
        String thisKey = "test" + UUID.randomUUID().toString();
        String lastName = UUID.randomUUID().toString();
        String thisEmail = "me@me.com" + " " + UUID.randomUUID();
        String firstName = "Bob";
        String name = firstName + " " + lastName;
        String locale = "EN-US";
        DateTimeZone timeZone = DateTimeZone.forID("America/Los_Angeles");
        int firstNameLength = firstName.length();

        return new DefaultAccount(UUID.randomUUID(), thisKey, thisEmail, name, firstNameLength, Currency.USD,
                billCycleDay, null, timeZone, locale,
                null, null, null, null, null, null, null, // add null address fields
                phone, "test", DateTime.now(), "test", DateTime.now());
    }

    @Test
    public void testBasic() throws EntityPersistenceException {
        Account a = createTestAccount(5);
        accountDao.create(a, context);
        String key = a.getExternalKey();

        Account r = accountDao.getAccountByKey(key);
        assertNotNull(r);
        assertEquals(r.getExternalKey(), a.getExternalKey());

        r = accountDao.getById(r.getId().toString());
        assertNotNull(r);
        assertEquals(r.getExternalKey(), a.getExternalKey());

        List<Account> all = accountDao.get();
        assertNotNull(all);
        assertTrue(all.size() >= 1);
    }

    // simple test to ensure long phone numbers can be stored
    @Test
    public void testLongPhoneNumber() throws EntityPersistenceException {
        Account account = createTestAccount(1, "123456789012345678901234");
        accountDao.create(account, context);

        Account saved = accountDao.getAccountByKey(account.getExternalKey());
        assertNotNull(saved);
    }

    // simple test to ensure excessively long phone numbers cannot be stored
    @Test(expectedExceptions = {EntityPersistenceException.class})
    public void testOverlyLongPhoneNumber() throws EntityPersistenceException {
        Account account = createTestAccount(1, "12345678901234567890123456");
        accountDao.create(account, context);
    }

    @Test
    public void testGetById() throws EntityPersistenceException {
        Account account = createTestAccount(1);
        UUID id = account.getId();
        String key = account.getExternalKey();
        String name = account.getName();
        int firstNameLength = account.getFirstNameLength();

        accountDao.create(account, context);

        account = accountDao.getById(id.toString());
        assertNotNull(account);
        assertEquals(account.getId(), id);
        assertEquals(account.getExternalKey(), key);
        assertEquals(account.getName(), name);
        assertEquals(account.getFirstNameLength(), firstNameLength);

    }

    @Test
    public void testCustomFields() throws EntityPersistenceException {
        Account account = createTestAccount(1);
        String fieldName = "testField1";
        String fieldValue = "testField1_value";
        account.setFieldValue(fieldName, fieldValue);

        accountDao.create(account, context);

        Account thisAccount = accountDao.getAccountByKey(account.getExternalKey());
        assertNotNull(thisAccount);
        assertEquals(thisAccount.getExternalKey(), account.getExternalKey());
        assertEquals(thisAccount.getFieldValue(fieldName), fieldValue);
    }

    @Test
    public void testTags() throws EntityPersistenceException {
        Account account = createTestAccount(1);
        TagDefinition definition = new DefaultTagDefinition("Test Tag", "For testing only");
        TagDefinitionSqlDao tagDescriptionDao = dbi.onDemand(TagDefinitionSqlDao.class);
        tagDescriptionDao.create(definition, context);

        account.addTag(definition);
        assertEquals(account.getTagList().size(), 1);
        accountDao.create(account, context);

        Account thisAccount = accountDao.getById(account.getId().toString());
        List<Tag> tagList = thisAccount.getTagList();
        assertEquals(tagList.size(), 1);
        Tag tag = tagList.get(0);
        assertEquals(tag.getTagDefinitionName(), definition.getName());
    }

    @Test
    public void testGetIdFromKey() throws EntityPersistenceException {
        Account account = createTestAccount(1);
        accountDao.create(account, context);

        try {
            UUID accountId = accountDao.getIdFromKey(account.getExternalKey());
            assertEquals(accountId, account.getId());
        } catch (AccountApiException a) {
            fail("Retrieving account failed.");
        }
    }

    @Test(expectedExceptions = AccountApiException.class)
    public void testGetIdFromKeyForNullKey() throws AccountApiException {
        String key = null;
        accountDao.getIdFromKey(key);
    }

    @Test
    public void testUpdate() throws Exception {
        final Account account = createTestAccount(1);
        accountDao.create(account, context);

        AccountData accountData = new AccountData() {
            @Override
            public String getExternalKey() {
                return account.getExternalKey();
            }

            @Override
            public String getName() {
                return "Jane Doe";
            }

            @Override
            public int getFirstNameLength() {
                return 4;
            }

            @Override
            public String getEmail() {
                return account.getEmail();
            }

            @Override
            public String getPhone() {
                return account.getPhone();
            }

            @Override
            public int getBillCycleDay() {
                return account.getBillCycleDay();
            }

            @Override
            public Currency getCurrency() {
                return account.getCurrency();
            }

            @Override
            public String getPaymentProviderName() {
                return account.getPaymentProviderName();
            }
            @Override
            public DateTimeZone getTimeZone() {
                return DateTimeZone.forID("Australia/Darwin");
            }

            @Override
            public String getLocale() {
                return "FR-CA";
            }
            @Override
            public String getAddress1() {
                return null;
            }

            @Override
            public String getAddress2() {
                return null;
            }

            @Override
            public String getCompanyName() {
                return null;
            }

            @Override
            public String getCity() {
                return null;
            }

            @Override
            public String getStateOrProvince() {
                return null;
            }

            @Override
            public String getPostalCode() {
                return null;
            }

            @Override
            public String getCountry() {
                return null;
            }
        };

        Account updatedAccount = new DefaultAccount(account.getId(), null, null, null, null, accountData);
        accountDao.update(updatedAccount, context);

        Account savedAccount = accountDao.getAccountByKey(account.getExternalKey());

        assertNotNull(savedAccount);
        assertEquals(savedAccount.getName(), updatedAccount.getName());
        assertEquals(savedAccount.getEmail(), updatedAccount.getEmail());
        assertEquals(savedAccount.getPaymentProviderName(), updatedAccount.getPaymentProviderName());
        assertEquals(savedAccount.getBillCycleDay(), updatedAccount.getBillCycleDay());
        assertEquals(savedAccount.getFirstNameLength(), updatedAccount.getFirstNameLength());
        assertEquals(savedAccount.getTimeZone(), updatedAccount.getTimeZone());
        assertEquals(savedAccount.getLocale(), updatedAccount.getLocale());
        assertEquals(savedAccount.getAddress1(), updatedAccount.getAddress1());
        assertEquals(savedAccount.getAddress2(), updatedAccount.getAddress2());
        assertEquals(savedAccount.getCity(), updatedAccount.getCity());
        assertEquals(savedAccount.getStateOrProvince(), updatedAccount.getStateOrProvince());
        assertEquals(savedAccount.getCountry(), updatedAccount.getCountry());
        assertEquals(savedAccount.getPostalCode(), updatedAccount.getPostalCode());
        assertEquals(savedAccount.getPhone(), updatedAccount.getPhone());
    }

    @Test
    public void testAddingContactInformation() throws Exception {
        UUID accountId = UUID.randomUUID();
        DefaultAccount account = new DefaultAccount(accountId, "extKey123456", "myemail123456@glam.com",
                                                    "John Smith", 4, Currency.USD, 15, null,
                                                    DateTimeZone.forID("America/Cambridge_Bay"), "EN-CA",
                                                    null, null, null, null, null, null, null, null, null, null, null, null);
        accountDao.create(account, context);

        String address1 = "123 address 1";
        String address2 = "456 address 2";
        String companyName = "Some Company";
        String city = "Cambridge Bay";
        String stateOrProvince = "Nunavut";
        String country = "Canada";
        String postalCode = "X0B 0C0";
        String phone = "18001112222";

        DefaultAccount updatedAccount = new DefaultAccount(accountId, "extKey123456", "myemail123456@glam.com",
                                                    "John Smith", 4, Currency.USD, 15, null,
                                                    DateTimeZone.forID("America/Cambridge_Bay"), "EN-CA",
                                                    address1, address2, companyName, city, stateOrProvince, country,
                                                    postalCode, phone, null, null, null, null);

        accountDao.update(updatedAccount, context);

        Account savedAccount = accountDao.getById(accountId.toString());

        assertNotNull(savedAccount);
        assertEquals(savedAccount.getId(), accountId);
        assertEquals(savedAccount.getAddress1(), address1);
        assertEquals(savedAccount.getAddress2(), address2);
        assertEquals(savedAccount.getCompanyName(), companyName);
        assertEquals(savedAccount.getCity(), city);
        assertEquals(savedAccount.getStateOrProvince(), stateOrProvince);
        assertEquals(savedAccount.getCity(), city);
        assertEquals(savedAccount.getPostalCode(), postalCode);
        assertEquals(savedAccount.getPhone(), phone);
    }

    @Test
    public void testRemovingContactInformation() throws Exception {
        UUID accountId = UUID.randomUUID();

        DefaultAccount account = new DefaultAccount(accountId, "extKey654321", "myemail654321@glam.com",
                                                    "John Smith", 4, Currency.USD, 15, null,
                                                    DateTimeZone.forID("America/Cambridge_Bay"), "EN-CA",
                                                    "123 address 1", "456 address 2", null, "Cambridge Bay",
                                                    "Nunavut", "Canada", "X0B 0C0", "18001112222",
                                                    null, null, null, null);
        accountDao.create(account, context);

        DefaultAccount updatedAccount = new DefaultAccount(accountId, "extKey654321", "myemail654321@glam.com",
                                                    "John Smith", 4, Currency.USD, 15, null,
                                                    DateTimeZone.forID("America/Cambridge_Bay"), "EN-CA",
                                                    null, null, null, null, null, null, null, null,
                                                    null, null, null, null);

        accountDao.update(updatedAccount, context);

        Account savedAccount = accountDao.getById(accountId.toString());

        assertNotNull(savedAccount);
        assertEquals(savedAccount.getId(), accountId);
        assertEquals(savedAccount.getAddress1(), null);
        assertEquals(savedAccount.getAddress2(), null);
        assertEquals(savedAccount.getCompanyName(), null);
        assertEquals(savedAccount.getCity(), null);
        assertEquals(savedAccount.getStateOrProvince(), null);
        assertEquals(savedAccount.getCity(), null);
        assertEquals(savedAccount.getPostalCode(), null);
        assertEquals(savedAccount.getPhone(), null);
    }

    @Test(expectedExceptions = EntityPersistenceException.class)
    public void testExternalKeyCannotBeUpdated() throws Exception {
        UUID accountId = UUID.randomUUID();
        String originalExternalKey = "extKey1337";

        DefaultAccount account = new DefaultAccount(accountId, originalExternalKey, "myemail1337@glam.com",
                                                    "John Smith", 4, Currency.USD, 15, null,
                                                    null, null, null, null, null, null, null, null, null, null,
                                                    null, null, null, null);
        accountDao.create(account, context);

        DefaultAccount updatedAccount = new DefaultAccount(accountId, "extKey1338", "myemail1337@glam.com",
                                                    "John Smith", 4, Currency.USD, 15, null,
                                                    null, null, null, null, null, null, null, null, null, null,
                                                    null, null, null, null);
        accountDao.update(updatedAccount, context);
    }
}
