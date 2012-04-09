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

package com.ning.billing.payment.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.ning.billing.util.callcontext.CallContext;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.model.DefaultInvoicePayment;
import com.ning.billing.payment.RetryService;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.payment.provider.PaymentProviderPlugin;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;
import com.ning.billing.payment.setup.PaymentConfig;

public class DefaultPaymentApi implements PaymentApi {
    private final PaymentProviderPluginRegistry pluginRegistry;
    private final AccountUserApi accountUserApi;
    private final InvoicePaymentApi invoicePaymentApi;
    private final RetryService retryService;
    private final PaymentDao paymentDao;
    private final PaymentConfig config;


    private static final Logger log = LoggerFactory.getLogger(DefaultPaymentApi.class);

    @Inject
    public DefaultPaymentApi(PaymentProviderPluginRegistry pluginRegistry,
                             AccountUserApi accountUserApi,
                             InvoicePaymentApi invoicePaymentApi,
                             RetryService retryService,
                             PaymentDao paymentDao,
                             PaymentConfig config) {
        this.pluginRegistry = pluginRegistry;
        this.accountUserApi = accountUserApi;
        this.invoicePaymentApi = invoicePaymentApi;
        this.retryService = retryService;
        this.paymentDao = paymentDao;
        this.config = config;
    }

    @Override
    public Either<PaymentError, PaymentMethodInfo> getPaymentMethod(@Nullable String accountKey, String paymentMethodId) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        return plugin.getPaymentMethodInfo(paymentMethodId);
    }

    private PaymentProviderPlugin getPaymentProviderPlugin(String accountKey) {
        String paymentProviderName = null;

        if (accountKey != null) {
            final Account account = accountUserApi.getAccountByKey(accountKey);
            if (account != null) {
                return getPaymentProviderPlugin(account);
            }
        }

        return pluginRegistry.getPlugin(paymentProviderName);
    }

    private PaymentProviderPlugin getPaymentProviderPlugin(Account account) {
        String paymentProviderName = null;

        if (account != null) {
            paymentProviderName = account.getPaymentProviderName();
        }

        return pluginRegistry.getPlugin(paymentProviderName);
    }

    @Override
    public Either<PaymentError, List<PaymentMethodInfo>> getPaymentMethods(String accountKey) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        return plugin.getPaymentMethods(accountKey);
    }

    @Override
    public Either<PaymentError, Void> updatePaymentGateway(String accountKey, CallContext context) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        return plugin.updatePaymentGateway(accountKey);
    }

    @Override
    public Either<PaymentError, PaymentProviderAccount> getPaymentProviderAccount(String accountKey) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        return plugin.getPaymentProviderAccount(accountKey);
    }

    @Override
    public Either<PaymentError, String> addPaymentMethod(@Nullable String accountKey, PaymentMethodInfo paymentMethod, CallContext context) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        return plugin.addPaymentMethod(accountKey, paymentMethod);
    }

    @Override
    public Either<PaymentError, Void> deletePaymentMethod(String accountKey, String paymentMethodId, CallContext context) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        return plugin.deletePaymentMethod(accountKey, paymentMethodId);
    }

    @Override
    public Either<PaymentError, PaymentMethodInfo> updatePaymentMethod(String accountKey, PaymentMethodInfo paymentMethodInfo, CallContext context) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        return plugin.updatePaymentMethod(accountKey, paymentMethodInfo);
    }

    @Override
    public List<Either<PaymentError, PaymentInfo>> createPayment(String accountKey, List<String> invoiceIds, CallContext context) {
        final Account account = accountUserApi.getAccountByKey(accountKey);
        return createPayment(account, invoiceIds, context);
    }

    @Override
    public Either<PaymentError, PaymentInfo> createPaymentForPaymentAttempt(UUID paymentAttemptId, CallContext context) {
        PaymentAttempt paymentAttempt = paymentDao.getPaymentAttemptById(paymentAttemptId);

        if (paymentAttempt != null) {
            Invoice invoice = invoicePaymentApi.getInvoice(paymentAttempt.getInvoiceId());
            Account account = accountUserApi.getAccountById(paymentAttempt.getAccountId());

            if (invoice != null && account != null) {
                if (invoice.getBalance().compareTo(BigDecimal.ZERO) <= 0 ) {
                    // TODO: send a notification that invoice was ignored?
                    log.info("Received invoice for payment with outstanding amount of 0 {} ", invoice);
                    return Either.left(new PaymentError("invoice_balance_0",
                                                        "Invoice balance was 0 or less",
                                                        paymentAttempt.getAccountId(),
                                                        paymentAttempt.getInvoiceId()));
                }
                else {
                    PaymentAttempt newPaymentAttempt = new PaymentAttempt.Builder(paymentAttempt)
                                                                         .setRetryCount(paymentAttempt.getRetryCount() + 1)
                                                                         .setPaymentAttemptId(UUID.randomUUID())
                                                                         .build();

                    paymentDao.createPaymentAttempt(newPaymentAttempt, context);
                    return processPayment(getPaymentProviderPlugin(account), account, invoice, newPaymentAttempt, context);
                }
            }
        }
        return Either.left(new PaymentError("retry_payment_error",
                                            "Could not load payment attempt, invoice or account for id " + paymentAttemptId,
                                            paymentAttempt.getAccountId(),
                                            paymentAttempt.getInvoiceId()));
    }

    @Override
    public List<Either<PaymentError, PaymentInfo>> createPayment(Account account, List<String> invoiceIds, CallContext context) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(account);

        List<Either<PaymentError, PaymentInfo>> processedPaymentsOrErrors = new ArrayList<Either<PaymentError, PaymentInfo>>(invoiceIds.size());

        for (String invoiceId : invoiceIds) {
            Invoice invoice = invoicePaymentApi.getInvoice(UUID.fromString(invoiceId));

            if (invoice.getBalance().compareTo(BigDecimal.ZERO) <= 0 ) {
                log.debug("Received invoice for payment with balance of 0 {} ", invoice);

            }
            else if (invoice.isMigrationInvoice()) {
            	log.info("Received invoice for payment that is a migration invoice - don't know how to handle those yet: {}", invoice);
            	Either<PaymentError, PaymentInfo> result = Either.left(new PaymentError("migration invoice",
                        "Invoice balance was a migration invoice",
                        account.getId(),
                        UUID.fromString(invoiceId)));
            			processedPaymentsOrErrors.add(result);
            }
            else {
                PaymentAttempt paymentAttempt = paymentDao.createPaymentAttempt(invoice, context);

                processedPaymentsOrErrors.add(processPayment(plugin, account, invoice, paymentAttempt, context));
            }
        }

        return processedPaymentsOrErrors;
    }

    private Either<PaymentError, PaymentInfo> processPayment(PaymentProviderPlugin plugin, Account account, Invoice invoice,
                                                             PaymentAttempt paymentAttempt, CallContext context) {
        Either<PaymentError, PaymentInfo> paymentOrError = plugin.processInvoice(account, invoice);
        PaymentInfo paymentInfo = null;

        if (paymentOrError.isLeft()) {
            String error = StringUtils.substring(paymentOrError.getLeft().getMessage() + paymentOrError.getLeft().getType(), 0, 100);
            log.info("Could not process a payment for " + paymentAttempt + " error was " + error);

            scheduleRetry(paymentAttempt, error);
        }
        else {
            paymentInfo = paymentOrError.getRight();
            paymentDao.savePaymentInfo(paymentInfo, context);

            final String paymentMethodId = paymentInfo.getPaymentMethodId();
            log.debug("Fetching payment method info for payment method id " + ((paymentMethodId == null) ? "null" : paymentMethodId));
            Either<PaymentError, PaymentMethodInfo> paymentMethodInfoOrError = plugin.getPaymentMethodInfo(paymentMethodId);

            if (paymentMethodInfoOrError.isRight()) {
                PaymentMethodInfo paymentMethodInfo = paymentMethodInfoOrError.getRight();

                if (paymentMethodInfo instanceof CreditCardPaymentMethodInfo) {
                    CreditCardPaymentMethodInfo ccPaymentMethod = (CreditCardPaymentMethodInfo)paymentMethodInfo;
                    paymentDao.updatePaymentInfo(ccPaymentMethod.getType(), paymentInfo.getPaymentId(), ccPaymentMethod.getCardType(), ccPaymentMethod.getCardCountry(), context);
                }
                else if (paymentMethodInfo instanceof PaypalPaymentMethodInfo) {
                    PaypalPaymentMethodInfo paypalPaymentMethodInfo = (PaypalPaymentMethodInfo)paymentMethodInfo;
                    paymentDao.updatePaymentInfo(paypalPaymentMethodInfo.getType(), paymentInfo.getPaymentId(), null, null, context);
                }
            } else {
                log.info(paymentMethodInfoOrError.getLeft().getMessage());
            }

            if (paymentInfo.getPaymentId() != null) {
                paymentDao.updatePaymentAttemptWithPaymentId(paymentAttempt.getPaymentAttemptId(), paymentInfo.getPaymentId(), context);
            }
        }

        invoicePaymentApi.notifyOfPaymentAttempt(new DefaultInvoicePayment(paymentAttempt.getPaymentAttemptId(),
                                                                           invoice.getId(),
                                                                           paymentAttempt.getPaymentAttemptDate(),
                                                                           paymentInfo == null || paymentInfo.getStatus().equalsIgnoreCase("Error") ? null : paymentInfo.getAmount(),
//                                                                         paymentInfo.getRefundAmount(), TODO
                                                                           paymentInfo == null || paymentInfo.getStatus().equalsIgnoreCase("Error") ? null : invoice.getCurrency()),
                                                                           context);

        return paymentOrError;
    }

    private void scheduleRetry(PaymentAttempt paymentAttempt, String error) {
        final List<Integer> retryDays = config.getPaymentRetryDays();

        int retryCount = 0;

        if (paymentAttempt.getRetryCount() != null) {
            retryCount = paymentAttempt.getRetryCount();
        }

        if (retryCount < retryDays.size()) {
            int retryInDays = 0;
            DateTime nextRetryDate = paymentAttempt.getPaymentAttemptDate();

            try {
                retryInDays = retryDays.get(retryCount);
                nextRetryDate = nextRetryDate.plusDays(retryInDays);
            }
            catch (NumberFormatException ex) {
                log.error("Could not get retry day for retry count {}", retryCount);
            }

            retryService.scheduleRetry(paymentAttempt, nextRetryDate);
        }
        else if (retryCount == retryDays.size()) {
            log.info("Last payment retry failed for {} ", paymentAttempt);
        }
        else {
            log.error("Cannot update payment retry information because retry count is invalid {} ", retryCount);
        }
    }

    @Override
    public Either<PaymentError, String> createPaymentProviderAccount(Account account, CallContext context) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin((Account)null);
        return plugin.createPaymentProviderAccount(account);
    }

    @Override
    public Either<PaymentError, Void> updatePaymentProviderAccountContact(String externalKey, CallContext context) {
    	Account account = accountUserApi.getAccountByKey(externalKey);
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(account);
        return plugin.updatePaymentProviderAccountExistingContact(account);
    }

    @Override
    public PaymentAttempt getPaymentAttemptForPaymentId(String id) {
        return paymentDao.getPaymentAttemptForPaymentId(id);
    }

    @Override
    public List<Either<PaymentError, PaymentInfo>> createRefund(Account account, List<String> invoiceIds) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(account);
        return plugin.processRefund(account);
    }

    @Override
    public List<PaymentInfo> getPaymentInfo(List<String> invoiceIds) {
        return paymentDao.getPaymentInfo(invoiceIds);
    }

    @Override
    public List<PaymentAttempt> getPaymentAttemptsForInvoiceId(String invoiceId) {
        return paymentDao.getPaymentAttemptsForInvoiceId(invoiceId);
    }

    @Override
    public PaymentInfo getPaymentInfoForPaymentAttemptId(String paymentAttemptId) {
        return paymentDao.getPaymentInfoForPaymentAttemptId(paymentAttemptId);
    }

}
