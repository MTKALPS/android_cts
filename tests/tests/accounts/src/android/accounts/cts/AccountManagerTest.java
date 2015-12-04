/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.accounts.cts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorDescription;
import android.accounts.AuthenticatorException;
import android.accounts.OnAccountsUpdateListener;
import android.accounts.OperationCanceledException;
import android.accounts.cts.common.Fixtures;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.StrictMode;
import android.test.ActivityInstrumentationTestCase2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * You can run those unit tests with the following command line:
 *
 *  adb shell am instrument
 *   -e debug false -w
 *   -e class android.accounts.cts.AccountManagerTest
 * android.accounts.cts/android.support.test.runner.AndroidJUnitRunner
 */
public class AccountManagerTest extends ActivityInstrumentationTestCase2<AccountDummyActivity> {

    public static final String ACCOUNT_NAME = "android.accounts.cts.account.name";
    public static final String ACCOUNT_NEW_NAME = "android.accounts.cts.account.name.rename";
    public static final String ACCOUNT_NAME_OTHER = "android.accounts.cts.account.name.other";

    public static final String ACCOUNT_TYPE = "android.accounts.cts.account.type";
    public static final String ACCOUNT_TYPE_CUSTOM = "android.accounts.cts.custom.account.type";
    public static final String ACCOUNT_TYPE_ABSENT = "android.accounts.cts.account.type.absent";

    public static final String ACCOUNT_PASSWORD = "android.accounts.cts.account.password";

    public static final String ACCOUNT_STATUS_TOKEN = "android.accounts.cts.account.status.token";

    public static final String AUTH_TOKEN_TYPE = "mockAuthTokenType";
    public static final String AUTH_EXPIRING_TOKEN_TYPE = "mockAuthExpiringTokenType";
    public static final String AUTH_TOKEN_LABEL = "mockAuthTokenLabel";
    public static final long AUTH_TOKEN_DURATION_MILLIS = 10000L; // Ten seconds.

    public static final String FEATURE_1 = "feature.1";
    public static final String FEATURE_2 = "feature.2";
    public static final String NON_EXISTING_FEATURE = "feature.3";

    public static final String OPTION_NAME_1 = "option.name.1";
    public static final String OPTION_VALUE_1 = "option.value.1";

    public static final String OPTION_NAME_2 = "option.name.2";
    public static final String OPTION_VALUE_2 = "option.value.2";

    public static final String[] REQUIRED_FEATURES = new String[] { FEATURE_1, FEATURE_2 };

    public static final Bundle OPTIONS_BUNDLE = new Bundle();

    public static final Bundle USERDATA_BUNDLE = new Bundle();

    public static final String USERDATA_NAME_1 = "user.data.name.1";
    public static final String USERDATA_NAME_2 = "user.data.name.2";
    public static final String USERDATA_VALUE_1 = "user.data.value.1";
    public static final String USERDATA_VALUE_2 = "user.data.value.2";

    public static final Account ACCOUNT = new Account(ACCOUNT_NAME, ACCOUNT_TYPE);
    public static final Account ACCOUNT_FOR_NEW_REMOVE_ACCOUNT_API = new Account(
            MockAccountAuthenticator.ACCOUNT_NAME_FOR_NEW_REMOVE_API, ACCOUNT_TYPE);
    public static final Account ACCOUNT_SAME_TYPE = new Account(ACCOUNT_NAME_OTHER, ACCOUNT_TYPE);

    public static final Account CUSTOM_TOKEN_ACCOUNT =
            new Account(ACCOUNT_NAME,ACCOUNT_TYPE_CUSTOM);

    public static final String SESSION_DATA_NAME_1 = "session.data.name.1";
    public static final String SESSION_DATA_NAME_2 = "session.data.name.2";
    public static final String SESSION_DATA_NAME_3 = "session.data.name.3";
    public static final String SESSION_DATA_VALUE_1 = "session.data.value.1";
    public static final int SESSION_DATA_VALUE_2 = 364;

    public static Bundle getSessionBundle(String accountName) {
        Bundle bundle = new Bundle();
        bundle.putString(SESSION_DATA_NAME_1, SESSION_DATA_VALUE_1);
        bundle.putInt(SESSION_DATA_NAME_2, SESSION_DATA_VALUE_2);
        // Test null value in Bundle.
        bundle.putParcelable(SESSION_DATA_NAME_3, null);
        bundle.putString(AccountManager.KEY_ACCOUNT_TYPE, ACCOUNT_TYPE);
        bundle.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        bundle.putAll(OPTIONS_BUNDLE);
        return bundle;
    }

    public static final String ERROR_MESSAGE = "android.accounts.cts.account.error.message";

    public static final String KEY_CIPHER = "cipher";
    public static final String KEY_MAC = "mac";

    private static MockAccountAuthenticator mockAuthenticator;
    private static final int LATCH_TIMEOUT_MS = 500;
    private static AccountManager am;

    public synchronized static MockAccountAuthenticator getMockAuthenticator(Context context) {
        if (null == mockAuthenticator) {
            mockAuthenticator = new MockAccountAuthenticator(context);
        }
        return mockAuthenticator;
    }

    private Activity mActivity;
    private Context mContext;

    public AccountManagerTest() {
        super(AccountDummyActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        mContext = getInstrumentation().getTargetContext();

        OPTIONS_BUNDLE.putString(OPTION_NAME_1, OPTION_VALUE_1);
        OPTIONS_BUNDLE.putString(OPTION_NAME_2, OPTION_VALUE_2);

        USERDATA_BUNDLE.putString(USERDATA_NAME_1, USERDATA_VALUE_1);

        getMockAuthenticator(mContext);

        am = AccountManager.get(mContext);
    }

    @Override
    public void tearDown() throws Exception, AuthenticatorException, OperationCanceledException {
        mockAuthenticator.clearData();

        // Need to clean up created account
        assertTrue(removeAccount(am, ACCOUNT, mActivity, null /* callback */).getBoolean(
                AccountManager.KEY_BOOLEAN_RESULT));
        assertTrue(removeAccount(am, ACCOUNT_SAME_TYPE, mActivity, null /* callback */).getBoolean(
                AccountManager.KEY_BOOLEAN_RESULT));

        // Clean out any other accounts added during the tests.
        Account[] ctsAccounts = am.getAccountsByType(ACCOUNT_TYPE);
        Account[] ctsCustomAccounts = am.getAccountsByType(ACCOUNT_TYPE_CUSTOM);
        ArrayList<Account> accounts = new ArrayList<>(Arrays.asList(ctsAccounts));
        accounts.addAll(Arrays.asList(ctsCustomAccounts));
        for (Account ctsAccount : accounts) {
            removeAccount(am, ctsAccount, mActivity, null /* callback */);
        }

        // need to clean up the authenticator cached data
        mockAuthenticator.clearData();

        super.tearDown();
    }

    interface TokenFetcher {
        public Bundle fetch(String tokenType)
                throws OperationCanceledException, AuthenticatorException, IOException;
        public Account getAccount();
    }

    private void validateSuccessfulTokenFetchingLifecycle(TokenFetcher fetcher, String tokenType)
            throws OperationCanceledException, AuthenticatorException, IOException {
        Account account = fetcher.getAccount();
        Bundle expected = new Bundle();
        expected.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
        expected.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);

        // First fetch.
        Bundle actual = fetcher.fetch(tokenType);
        assertTrue(mockAuthenticator.isRecentlyCalled());
        validateAccountAndAuthTokenResult(expected, actual);

        /*
         * On the second fetch the cache will be populated if we are using a authenticator with
         * customTokens=false or we are using a scope that will cause the authenticator to set an
         * expiration time (and that expiration time hasn't been reached).
         */
        actual = fetcher.fetch(tokenType);

        boolean isCachingExpected =
                ACCOUNT_TYPE.equals(account.type) || AUTH_EXPIRING_TOKEN_TYPE.equals(tokenType);
        assertEquals(isCachingExpected, !mockAuthenticator.isRecentlyCalled());
        validateAccountAndAuthTokenResult(expected, actual);

        try {
            // Delay further execution until expiring tokens can actually expire.
            Thread.sleep(mockAuthenticator.getTokenDurationMillis() + 1L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        /*
         * With the time shift above, the third request will result in cache hits only from
         * customToken=false authenticators.
         */
        actual = fetcher.fetch(tokenType);
        isCachingExpected = ACCOUNT_TYPE.equals(account.type);
        assertEquals(isCachingExpected, !mockAuthenticator.isRecentlyCalled());
        validateAccountAndAuthTokenResult(expected, actual);

        // invalidate token
        String token = actual.getString(AccountManager.KEY_AUTHTOKEN);
        am.invalidateAuthToken(account.type, token);

        /*
         * Upon invalidating the token, the cache should be clear regardless of authenticator.
         */
        actual = fetcher.fetch(tokenType);
        assertTrue(mockAuthenticator.isRecentlyCalled());
        validateAccountAndAuthTokenResult(expected, actual);
    }

    private void validateAccountAndAuthTokenResult(Bundle actual) {
        assertEquals(
                ACCOUNT.name,
                actual.get(AccountManager.KEY_ACCOUNT_NAME));
        assertEquals(
                ACCOUNT.type,
                actual.get(AccountManager.KEY_ACCOUNT_TYPE));
        assertEquals(
                mockAuthenticator.getLastTokenServed(),
                actual.get(AccountManager.KEY_AUTHTOKEN));
    }

    private void validateAccountAndAuthTokenResult(Bundle expected, Bundle actual) {
        assertEquals(
                expected.get(AccountManager.KEY_ACCOUNT_NAME),
                actual.get(AccountManager.KEY_ACCOUNT_NAME));
        assertEquals(
                expected.get(AccountManager.KEY_ACCOUNT_TYPE),
                actual.get(AccountManager.KEY_ACCOUNT_TYPE));
        assertEquals(
                mockAuthenticator.getLastTokenServed(),
                actual.get(AccountManager.KEY_AUTHTOKEN));
    }

    private void validateAccountAndNoAuthTokenResult(Bundle result) {
        assertEquals(ACCOUNT_NAME, result.get(AccountManager.KEY_ACCOUNT_NAME));
        assertEquals(ACCOUNT_TYPE, result.get(AccountManager.KEY_ACCOUNT_TYPE));
        assertNull(result.get(AccountManager.KEY_AUTHTOKEN));
    }

    private void validateNullResult(Bundle resultBundle) {
        assertNull(resultBundle.get(AccountManager.KEY_ACCOUNT_NAME));
        assertNull(resultBundle.get(AccountManager.KEY_ACCOUNT_TYPE));
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
    }

    private void validateAccountAndAuthTokenType() {
        assertEquals(ACCOUNT_TYPE, mockAuthenticator.getAccountType());
        assertEquals(AUTH_TOKEN_TYPE, mockAuthenticator.getAuthTokenType());
    }

    private void validateFeatures() {
        assertEquals(REQUIRED_FEATURES[0], mockAuthenticator.getRequiredFeatures()[0]);
        assertEquals(REQUIRED_FEATURES[1], mockAuthenticator.getRequiredFeatures()[1]);
    }

    private void validateOptions(Bundle expectedOptions, Bundle actualOptions) {
        // In ICS AccountManager may add options to indicate the caller id.
        // We only validate that the passed in options are present in the actual ones
        if (expectedOptions != null) {
            assertNotNull(actualOptions);
            assertEquals(expectedOptions.get(OPTION_NAME_1), actualOptions.get(OPTION_NAME_1));
            assertEquals(expectedOptions.get(OPTION_NAME_2), actualOptions.get(OPTION_NAME_2));
        }
    }

    private void validateSystemOptions(Bundle options) {
        assertNotNull(options.getString(AccountManager.KEY_ANDROID_PACKAGE_NAME));
        assertTrue(options.containsKey(AccountManager.KEY_CALLER_UID));
        assertTrue(options.containsKey(AccountManager.KEY_CALLER_PID));
    }

    private void validateCredentials() {
        assertEquals(ACCOUNT, mockAuthenticator.getAccount());
    }

    private int getAccountsCount() {
        Account[] accounts = am.getAccounts();
        assertNotNull(accounts);
        return accounts.length;
    }

    private Bundle addAccount(AccountManager am, String accountType, String authTokenType,
            String[] requiredFeatures, Bundle options, Activity activity,
            AccountManagerCallback<Bundle> callback, Handler handler) throws
                IOException, AuthenticatorException, OperationCanceledException {

        AccountManagerFuture<Bundle> futureBundle = am.addAccount(
                accountType,
                authTokenType,
                requiredFeatures,
                options,
                activity,
                callback,
                handler);

        Bundle resultBundle = futureBundle.getResult();
        assertTrue(futureBundle.isDone());
        assertNotNull(resultBundle);

        return resultBundle;
    }

    private Account renameAccount(AccountManager am, Account account, String newName)
            throws OperationCanceledException, AuthenticatorException, IOException {
        AccountManagerFuture<Account> futureAccount = am.renameAccount(
                account, newName, null /* callback */, null /* handler */);
        Account renamedAccount = futureAccount.getResult();
        assertTrue(futureAccount.isDone());
        assertNotNull(renamedAccount);
        return renamedAccount;
    }

    private boolean removeAccount(AccountManager am, Account account,
            AccountManagerCallback<Boolean> callback) throws IOException, AuthenticatorException,
                OperationCanceledException {
        AccountManagerFuture<Boolean> futureBoolean = am.removeAccount(account,
                callback,
                null /* handler */);
        Boolean resultBoolean = futureBoolean.getResult();
        assertTrue(futureBoolean.isDone());

        return resultBoolean;
    }

    private Bundle removeAccountWithIntentLaunch(AccountManager am, Account account,
            Activity activity, AccountManagerCallback<Bundle> callback) throws IOException,
            AuthenticatorException, OperationCanceledException {

        AccountManagerFuture<Bundle> futureBundle = am.removeAccount(account,
                activity,
                callback,
                null /* handler */);
        Bundle resultBundle = futureBundle.getResult();
        assertTrue(futureBundle.isDone());

        return resultBundle;
    }

    private Bundle removeAccount(AccountManager am, Account account, Activity activity,
            AccountManagerCallback<Bundle> callback) throws IOException, AuthenticatorException,
                OperationCanceledException {

        AccountManagerFuture<Bundle> futureBundle = am.removeAccount(account,
                activity,
                callback,
                null /* handler */);
        Bundle resultBundle = futureBundle.getResult();
        assertTrue(futureBundle.isDone());

        return resultBundle;
    }

    private boolean removeAccountExplicitly(AccountManager am, Account account) {
        return am.removeAccountExplicitly(account);
    }

    private void addAccountExplicitly(Account account, String password, Bundle userdata) {
        assertTrue(am.addAccountExplicitly(account, password, userdata));
    }

    private Bundle getAuthTokenByFeature(String[] features, Activity activity)
            throws IOException, AuthenticatorException, OperationCanceledException {

        AccountManagerFuture<Bundle> futureBundle = am.getAuthTokenByFeatures(ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                features,
                activity,
                OPTIONS_BUNDLE,
                OPTIONS_BUNDLE,
                null /* no callback */,
                null /* no handler */
        );

        Bundle resultBundle = futureBundle.getResult();

        assertTrue(futureBundle.isDone());
        assertNotNull(resultBundle);

        return resultBundle;
    }

    private boolean isAccountPresent(Account[] accounts, Account accountToCheck) {
        if (null == accounts || null == accountToCheck) {
            return false;
        }
        boolean result = false;
        int length = accounts.length;
        for (int n=0; n<length; n++) {
            if(accountToCheck.equals(accounts[n])) {
                result = true;
                break;
            }
        }
        return result;
    }

    /**
     * Test singleton
     */
    public void testGet() {
        assertNotNull(AccountManager.get(mContext));
    }

    /**
     * Test a basic addAccount()
     */
    public void testAddAccount() throws IOException, AuthenticatorException,
            OperationCanceledException {

        Bundle resultBundle = addAccount(am,
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                REQUIRED_FEATURES,
                OPTIONS_BUNDLE,
                mActivity,
                null /* callback */,
                null /* handler */);

        // Assert parameters has been passed correctly
        validateAccountAndAuthTokenType();
        validateFeatures();
        validateOptions(OPTIONS_BUNDLE, mockAuthenticator.mOptionsAddAccount);
        validateSystemOptions(mockAuthenticator.mOptionsAddAccount);
        validateOptions(null, mockAuthenticator.mOptionsUpdateCredentials);
        validateOptions(null, mockAuthenticator.mOptionsConfirmCredentials);
        validateOptions(null, mockAuthenticator.mOptionsGetAuthToken);

        // Assert returned result
        validateAccountAndNoAuthTokenResult(resultBundle);
    }

    /**
     * Test addAccount() with callback and handler
     */
    public void testAddAccountWithCallbackAndHandler() throws IOException,
            AuthenticatorException, OperationCanceledException {

        testAddAccountWithCallbackAndHandler(null /* handler */);
        testAddAccountWithCallbackAndHandler(new Handler(Looper.getMainLooper()));
    }

    private void testAddAccountWithCallbackAndHandler(Handler handler) throws IOException,
            AuthenticatorException, OperationCanceledException {

        final CountDownLatch latch = new CountDownLatch(1);

        AccountManagerCallback<Bundle> callback = new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> bundleFuture) {
                Bundle resultBundle = null;
                try {
                    resultBundle = bundleFuture.getResult();
                } catch (OperationCanceledException e) {
                    fail("should not throw an OperationCanceledException");
                } catch (IOException e) {
                    fail("should not throw an IOException");
                } catch (AuthenticatorException e) {
                    fail("should not throw an AuthenticatorException");
                }

                // Assert parameters has been passed correctly
                validateAccountAndAuthTokenType();
                validateFeatures();
                validateOptions(OPTIONS_BUNDLE, mockAuthenticator.mOptionsAddAccount);
                validateOptions(null, mockAuthenticator.mOptionsUpdateCredentials);
                validateOptions(null, mockAuthenticator.mOptionsConfirmCredentials);
                validateOptions(null, mockAuthenticator.mOptionsGetAuthToken);

                // Assert return result
                validateAccountAndNoAuthTokenResult(resultBundle);

                latch.countDown();
            }
        };

        addAccount(am,
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                REQUIRED_FEATURES,
                OPTIONS_BUNDLE,
                mActivity,
                callback,
                handler);

        // Wait with timeout for the callback to do its work
        try {
            latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }
    }

    /**
     * Test addAccountExplicitly(), renameAccount() and removeAccount().
     */
    public void testAddAccountExplicitlyAndRemoveAccount() throws IOException,
            AuthenticatorException, OperationCanceledException {

        final int expectedAccountsCount = getAccountsCount();

        addAccountExplicitly(ACCOUNT, ACCOUNT_PASSWORD, null /* userData */);

        // Assert that we have one more account
        Account[] accounts = am.getAccounts();
        assertNotNull(accounts);
        assertEquals(1 + expectedAccountsCount, accounts.length);
        assertTrue(isAccountPresent(am.getAccounts(), ACCOUNT));
        // Need to clean up
        assertTrue(removeAccount(am, ACCOUNT, mActivity, null /* callback */).getBoolean(
                AccountManager.KEY_BOOLEAN_RESULT));

        // and verify that we go back to the initial state
        accounts = am.getAccounts();
        assertNotNull(accounts);
        assertEquals(expectedAccountsCount, accounts.length);
    }

    /**
     * Test addAccountExplicitly(), renameAccount() and removeAccount().
     */
    public void testAddAccountExplicitlyAndRemoveAccountWithNewApi() throws IOException,
            AuthenticatorException, OperationCanceledException {

        final int expectedAccountsCount = getAccountsCount();

        addAccountExplicitly(ACCOUNT_FOR_NEW_REMOVE_ACCOUNT_API, ACCOUNT_PASSWORD, null /* userData */);

        // Assert that we have one more account
        Account[] accounts = am.getAccounts();
        assertNotNull(accounts);
        assertEquals(1 + expectedAccountsCount, accounts.length);
        assertTrue(isAccountPresent(am.getAccounts(), ACCOUNT_FOR_NEW_REMOVE_ACCOUNT_API));
        // Deprecated API should not work
        assertFalse(removeAccount(am, ACCOUNT_FOR_NEW_REMOVE_ACCOUNT_API, null /* callback */));
        accounts = am.getAccounts();
        assertNotNull(accounts);
        assertEquals(1 + expectedAccountsCount, accounts.length);
        assertTrue(isAccountPresent(am.getAccounts(), ACCOUNT_FOR_NEW_REMOVE_ACCOUNT_API));
        // Check removal of account
        assertTrue(removeAccountWithIntentLaunch(am, ACCOUNT_FOR_NEW_REMOVE_ACCOUNT_API, mActivity, null /* callback */)
                .getBoolean(AccountManager.KEY_BOOLEAN_RESULT));
        // and verify that we go back to the initial state
        accounts = am.getAccounts();
        assertNotNull(accounts);
        assertEquals(expectedAccountsCount, accounts.length);
    }

    /**
     * Test addAccountExplicitly(), renameAccount() and removeAccount().
     */
    public void testAddAccountExplicitlyAndRemoveAccountWithDeprecatedApi() throws IOException,
            AuthenticatorException, OperationCanceledException {

        final int expectedAccountsCount = getAccountsCount();

        addAccountExplicitly(ACCOUNT, ACCOUNT_PASSWORD, null /* userData */);

        // Assert that we have one more account
        Account[] accounts = am.getAccounts();
        assertNotNull(accounts);
        assertEquals(1 + expectedAccountsCount, accounts.length);
        assertTrue(isAccountPresent(am.getAccounts(), ACCOUNT));
        // Need to clean up
        assertTrue(removeAccount(am, ACCOUNT, null /* callback */));

        // and verify that we go back to the initial state
        accounts = am.getAccounts();
        assertNotNull(accounts);
        assertEquals(expectedAccountsCount, accounts.length);
    }

    /**
     * Test addAccountExplicitly() and removeAccountExplictly().
     */
    public void testAddAccountExplicitlyAndRemoveAccountExplicitly() {
        final int expectedAccountsCount = getAccountsCount();

        addAccountExplicitly(ACCOUNT, ACCOUNT_PASSWORD, null /* userData */);

        // Assert that we have one more account
        Account[] accounts = am.getAccounts();
        assertNotNull(accounts);
        assertEquals(1 + expectedAccountsCount, accounts.length);
        assertTrue(isAccountPresent(am.getAccounts(), ACCOUNT));
        // Need to clean up
        assertTrue(removeAccountExplicitly(am, ACCOUNT));

        // and verify that we go back to the initial state
        accounts = am.getAccounts();
        assertNotNull(accounts);
        assertEquals(expectedAccountsCount, accounts.length);
    }

    /**
     * Test setUserData() and getUserData().
     */
    public void testAccountRenameAndGetPreviousName()
            throws OperationCanceledException, AuthenticatorException, IOException {
        // Add a first account
        boolean result = am.addAccountExplicitly(ACCOUNT,
                                ACCOUNT_PASSWORD,
                                USERDATA_BUNDLE);
        assertTrue(result);

        // Prior to a renmae, the previous name should be null.
        String nullName = am.getPreviousName(ACCOUNT);
        assertNull(nullName);

        final int expectedAccountsCount = getAccountsCount();

        Account renamedAccount = renameAccount(am, ACCOUNT, ACCOUNT_NEW_NAME);

        /*
         *  Make sure that the resultant renamed account has the correct name
         *  and is associated with the correct account type.
         */
        assertEquals(ACCOUNT_NEW_NAME, renamedAccount.name);
        assertEquals(ACCOUNT.type, renamedAccount.type);

        // Make sure the total number of accounts is the same.
        Account[] accounts = am.getAccounts();
        assertEquals(expectedAccountsCount, accounts.length);

        // Make sure the old account isn't present.
        assertFalse(isAccountPresent(am.getAccounts(), ACCOUNT));

        // But that the new one is.
        assertTrue(isAccountPresent(am.getAccounts(), renamedAccount));

        // Check that the UserData is still present.
        assertEquals(USERDATA_VALUE_1, am.getUserData(renamedAccount, USERDATA_NAME_1));

        assertEquals(ACCOUNT.name, am.getPreviousName(renamedAccount));

       // Need to clean up
        assertTrue(removeAccount(am, renamedAccount, mActivity, null /* callback */).getBoolean(
                AccountManager.KEY_BOOLEAN_RESULT));
    }

    /**
     * Test getAccounts() and getAccountsByType()
     */
    public void testGetAccountsAndGetAccountsByType() {

        assertEquals(false, isAccountPresent(am.getAccounts(), ACCOUNT));
        assertEquals(false, isAccountPresent(am.getAccounts(), ACCOUNT_SAME_TYPE));

        final int accountsCount = getAccountsCount();

        // Add a first account
        addAccountExplicitly(ACCOUNT, ACCOUNT_PASSWORD, null /* userData */);

        // Check that we have the new account
        Account[] accounts = am.getAccounts();
        assertEquals(1 + accountsCount, accounts.length);
        assertEquals(true, isAccountPresent(accounts, ACCOUNT));

        // Add another account
        addAccountExplicitly(ACCOUNT_SAME_TYPE, ACCOUNT_PASSWORD, null /* userData */);

        // Check that we have one more account again
        accounts = am.getAccounts();
        assertEquals(2 + accountsCount, accounts.length);
        assertEquals(true, isAccountPresent(accounts, ACCOUNT_SAME_TYPE));

        // Check if we have one from first type
        accounts = am.getAccountsByType(ACCOUNT_TYPE);
        assertEquals(2, accounts.length);

        // Check if we dont have any account from the other type
        accounts = am.getAccountsByType(ACCOUNT_TYPE_ABSENT);
        assertEquals(0, accounts.length);
    }

    /**
     * Test getAuthenticatorTypes()
     */
    public void testGetAuthenticatorTypes() {
        AuthenticatorDescription[] types = am.getAuthenticatorTypes();
        for(AuthenticatorDescription description: types) {
            if (description.type.equals(ACCOUNT_TYPE)) {
                return;
            }
        }
        fail("should have found Authenticator type: " + ACCOUNT_TYPE);
    }

    /**
     * Test setPassword() and getPassword()
     */
    public void testSetAndGetAndClearPassword() {
        // Add a first account
        addAccountExplicitly(ACCOUNT, ACCOUNT_PASSWORD, null /* userData */);

        // Check that the password is the one we defined
        assertEquals(ACCOUNT_PASSWORD, am.getPassword(ACCOUNT));

        // Clear the password and check that it is cleared
        am.clearPassword(ACCOUNT);
        assertNull(am.getPassword(ACCOUNT));

        // Reset the password
        am.setPassword(ACCOUNT, ACCOUNT_PASSWORD);

        // Check that the password is the one we defined
        assertEquals(ACCOUNT_PASSWORD, am.getPassword(ACCOUNT));
    }

    /**
     * Test setUserData() and getUserData()
     */
    public void testSetAndGetUserData() {
        // Add a first account
        boolean result = am.addAccountExplicitly(ACCOUNT,
                                ACCOUNT_PASSWORD,
                                USERDATA_BUNDLE);

        assertTrue(result);

        // Check that the UserData is the one we defined
        assertEquals(USERDATA_VALUE_1, am.getUserData(ACCOUNT, USERDATA_NAME_1));

        am.setUserData(ACCOUNT, USERDATA_NAME_2, USERDATA_VALUE_2);

        // Check that the UserData is the one we defined
        assertEquals(USERDATA_VALUE_2, am.getUserData(ACCOUNT, USERDATA_NAME_2));
    }

    /**
     * Test getAccountsByTypeAndFeatures()
     */
    public void testGetAccountsByTypeAndFeatures() throws IOException,
            AuthenticatorException, OperationCanceledException {

        addAccountExplicitly(ACCOUNT, ACCOUNT_PASSWORD, null /* userData */);

        AccountManagerFuture<Account[]> futureAccounts = am.getAccountsByTypeAndFeatures(
                ACCOUNT_TYPE, REQUIRED_FEATURES, null, null);

        Account[] accounts = futureAccounts.getResult();

        assertNotNull(accounts);
        assertEquals(1, accounts.length);
        assertEquals(true, isAccountPresent(accounts, ACCOUNT));

        futureAccounts = am.getAccountsByTypeAndFeatures(ACCOUNT_TYPE,
                new String[] { NON_EXISTING_FEATURE },
                null /* callback*/,
                null /* handler */);
        accounts = futureAccounts.getResult();

        assertNotNull(accounts);
        assertEquals(0, accounts.length);
    }

    /**
     * Test getAccountsByTypeAndFeatures() with callback and handler
     */
    public void testGetAccountsByTypeAndFeaturesWithCallbackAndHandler() throws IOException,
            AuthenticatorException, OperationCanceledException {

        addAccountExplicitly(ACCOUNT, ACCOUNT_PASSWORD, null /* userData */);

        testGetAccountsByTypeAndFeaturesWithCallbackAndHandler(null /* handler */);
        testGetAccountsByTypeAndFeaturesWithCallbackAndHandler(new Handler(Looper.getMainLooper()));
    }

    private void testGetAccountsByTypeAndFeaturesWithCallbackAndHandler(Handler handler) throws
            IOException, AuthenticatorException, OperationCanceledException {

        final CountDownLatch latch1 = new CountDownLatch(1);

        AccountManagerCallback<Account[]> callback1 = new AccountManagerCallback<Account[]>() {
            @Override
            public void run(AccountManagerFuture<Account[]> accountsFuture) {
                try {
                    Account[] accounts = accountsFuture.getResult();
                    assertNotNull(accounts);
                    assertEquals(1, accounts.length);
                    assertEquals(true, isAccountPresent(accounts, ACCOUNT));
                } catch (OperationCanceledException e) {
                    fail("should not throw an OperationCanceledException");
                } catch (IOException e) {
                    fail("should not throw an IOException");
                } catch (AuthenticatorException e) {
                    fail("should not throw an AuthenticatorException");
                } finally {
                  latch1.countDown();
                }
            }
        };

        AccountManagerFuture<Account[]> futureAccounts = am.getAccountsByTypeAndFeatures(
                ACCOUNT_TYPE,
                REQUIRED_FEATURES,
                callback1,
                handler);

        Account[] accounts = futureAccounts.getResult();

        assertNotNull(accounts);
        assertEquals(1, accounts.length);
        assertEquals(true, isAccountPresent(accounts, ACCOUNT));

        // Wait with timeout for the callback to do its work
        try {
            latch1.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }

        final CountDownLatch latch2 = new CountDownLatch(1);

        AccountManagerCallback<Account[]> callback2 = new AccountManagerCallback<Account[]>() {
            @Override
            public void run(AccountManagerFuture<Account[]> accountsFuture) {
                try {
                    Account[] accounts = accountsFuture.getResult();
                    assertNotNull(accounts);
                    assertEquals(0, accounts.length);
                } catch (OperationCanceledException e) {
                    fail("should not throw an OperationCanceledException");
                } catch (IOException e) {
                    fail("should not throw an IOException");
                } catch (AuthenticatorException e) {
                    fail("should not throw an AuthenticatorException");
                } finally {
                  latch2.countDown();
                }
            }
        };

        accounts = null;

        futureAccounts = am.getAccountsByTypeAndFeatures(ACCOUNT_TYPE,
                new String[] { NON_EXISTING_FEATURE },
                callback2,
                handler);

        accounts = futureAccounts.getResult();
        assertNotNull(accounts);
        assertEquals(0, accounts.length);

        // Wait with timeout for the callback to do its work
        try {
            latch2.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }
    }

    /**
     * Test setAuthToken() and peekAuthToken()
     */
    public void testSetAndPeekAndInvalidateAuthToken() {
        addAccountExplicitly(ACCOUNT, ACCOUNT_PASSWORD, null /* userData */);
        String expected = "x";
        am.setAuthToken(ACCOUNT, AUTH_TOKEN_TYPE, expected);

        // Ask for the AuthToken
        String token = am.peekAuthToken(ACCOUNT, AUTH_TOKEN_TYPE);
        assertNotNull(token);
        assertEquals(expected, token);

        am.invalidateAuthToken(ACCOUNT_TYPE, token);
        token = am.peekAuthToken(ACCOUNT, AUTH_TOKEN_TYPE);
        assertNull(token);
    }

    /**
     * Test successful blockingGetAuthToken() with customTokens=false authenticator.
     */
    public void testBlockingGetAuthToken_DefaultToken_Success()
            throws IOException, AuthenticatorException, OperationCanceledException {
        addAccountExplicitly(ACCOUNT, ACCOUNT_PASSWORD, null);

        String token = am.blockingGetAuthToken(ACCOUNT,
                AUTH_TOKEN_TYPE,
                false /* no failure notification */);

        // Ask for the AuthToken
        assertNotNull(token);
        assertEquals(mockAuthenticator.getLastTokenServed(), token);
    }

    private static class BlockingGetAuthTokenFetcher implements TokenFetcher {
        private final Account mAccount;

        BlockingGetAuthTokenFetcher(Account account) {
            mAccount = account;
        }

        @Override
        public Bundle fetch(String tokenType)
                throws OperationCanceledException, AuthenticatorException, IOException {
            String token = am.blockingGetAuthToken(
                    getAccount(),
                    tokenType,
                    false /* no failure notification */);
            Bundle result = new Bundle();
            result.putString(AccountManager.KEY_AUTHTOKEN, token);
            result.putString(AccountManager.KEY_ACCOUNT_NAME, mAccount.name);
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, mAccount.type);
            return result;
        }
        @Override
        public Account getAccount() {
            return CUSTOM_TOKEN_ACCOUNT;
        }
    }

    /**
     * Test successful blockingGetAuthToken() with customTokens=true authenticator.
     */
    public void testBlockingGetAuthToken_CustomToken_NoCaching_Success()
            throws IOException, AuthenticatorException, OperationCanceledException {
        addAccountExplicitly(CUSTOM_TOKEN_ACCOUNT, ACCOUNT_PASSWORD, null);
        TokenFetcher f = new BlockingGetAuthTokenFetcher(CUSTOM_TOKEN_ACCOUNT);
        validateSuccessfulTokenFetchingLifecycle(f, AUTH_TOKEN_TYPE);
    }

    /**
     * Test successful blockingGetAuthToken() with customTokens=true authenticator.
     */
    public void testBlockingGetAuthToken_CustomToken_ExpiringCache_Success()
            throws IOException, AuthenticatorException, OperationCanceledException {
        addAccountExplicitly(CUSTOM_TOKEN_ACCOUNT, ACCOUNT_PASSWORD, null);
        TokenFetcher f = new BlockingGetAuthTokenFetcher(CUSTOM_TOKEN_ACCOUNT);
        validateSuccessfulTokenFetchingLifecycle(f, AUTH_EXPIRING_TOKEN_TYPE);
    }

    /**
     * Test successful getAuthToken() using a future with customTokens=false authenticator.
     */
    public void testDeprecatedGetAuthTokenWithFuture_NoOptions_DefaultToken_Success()
            throws IOException, AuthenticatorException, OperationCanceledException {
        addAccountExplicitly(ACCOUNT, ACCOUNT_PASSWORD, null /* userData */);
        AccountManagerFuture<Bundle> futureBundle = am.getAuthToken(ACCOUNT,
                AUTH_TOKEN_TYPE,
                false /* no failure notification */,
                null /* no callback */,
                null /* no handler */
        );

        Bundle resultBundle = futureBundle.getResult();

        assertTrue(futureBundle.isDone());
        assertNotNull(resultBundle);

        // Assert returned result
        validateAccountAndAuthTokenResult(resultBundle);
    }

    /**
     * Test successful getAuthToken() using a future with customTokens=false without
     * expiring tokens.
     */
    public void testDeprecatedGetAuthTokenWithFuture_NoOptions_CustomToken_Success()
            throws IOException, AuthenticatorException, OperationCanceledException {
        addAccountExplicitly(CUSTOM_TOKEN_ACCOUNT, ACCOUNT_PASSWORD, null);
        // validateSuccessfulTokenFetchingLifecycle(AccountManager am, TokenFetcher fetcher, String tokenType)
        TokenFetcher f = new TokenFetcher() {
            @Override
            public Bundle fetch(String tokenType)
                    throws OperationCanceledException, AuthenticatorException, IOException {
                AccountManagerFuture<Bundle> futureBundle = am.getAuthToken(
                        getAccount(),
                        tokenType,
                        false /* no failure notification */,
                        null /* no callback */,
                        null /* no handler */
                );
                Bundle actual = futureBundle.getResult();
                assertTrue(futureBundle.isDone());
                assertNotNull(actual);
                return actual;
            }

            @Override
            public Account getAccount() {
                return CUSTOM_TOKEN_ACCOUNT;
            }
        };
        validateSuccessfulTokenFetchingLifecycle(f, AUTH_EXPIRING_TOKEN_TYPE);
        validateSuccessfulTokenFetchingLifecycle(f, AUTH_TOKEN_TYPE);
    }

    /**
     * Test successful getAuthToken() using a future with customTokens=false without
     * expiring tokens.
     */
    public void testGetAuthTokenWithFuture_Options_DefaultToken_Success()
            throws IOException, AuthenticatorException, OperationCanceledException {
        addAccountExplicitly(ACCOUNT, ACCOUNT_PASSWORD, null /* userData */);

        AccountManagerFuture<Bundle> futureBundle = am.getAuthToken(ACCOUNT,
                AUTH_TOKEN_TYPE,
                OPTIONS_BUNDLE,
                mActivity,
                null /* no callback */,
                null /* no handler */
        );

        Bundle resultBundle = futureBundle.getResult();

        assertTrue(futureBundle.isDone());
        assertNotNull(resultBundle);

        // Assert returned result
        validateAccountAndAuthTokenResult(resultBundle);

        validateOptions(null, mockAuthenticator.mOptionsAddAccount);
        validateOptions(null, mockAuthenticator.mOptionsUpdateCredentials);
        validateOptions(null, mockAuthenticator.mOptionsConfirmCredentials);
        validateOptions(OPTIONS_BUNDLE, mockAuthenticator.mOptionsGetAuthToken);
        validateSystemOptions(mockAuthenticator.mOptionsGetAuthToken);
    }

    /**
     * Test successful getAuthToken() using a future with customTokens=false without
     * expiring tokens.
     */
    public void testGetAuthTokenWithFuture_Options_CustomToken_Success()
            throws IOException, AuthenticatorException, OperationCanceledException {
        addAccountExplicitly(CUSTOM_TOKEN_ACCOUNT, ACCOUNT_PASSWORD, null);
        TokenFetcher fetcherWithOptions = new TokenFetcher() {
            @Override
            public Bundle fetch(String tokenType)
                    throws OperationCanceledException, AuthenticatorException, IOException {
                AccountManagerFuture<Bundle> futureBundle = am.getAuthToken(
                        getAccount(),
                        tokenType,
                        OPTIONS_BUNDLE,
                        false /* no failure notification */,
                        null /* no callback */,
                        null /* no handler */
                );
                Bundle actual = futureBundle.getResult();
                assertTrue(futureBundle.isDone());
                assertNotNull(actual);
                return actual;
            }

            @Override
            public Account getAccount() {
                return CUSTOM_TOKEN_ACCOUNT;
            }
        };
        validateSuccessfulTokenFetchingLifecycle(fetcherWithOptions, AUTH_TOKEN_TYPE);
        validateSuccessfulTokenFetchingLifecycle(fetcherWithOptions, AUTH_EXPIRING_TOKEN_TYPE);
    }


    /**
     * Test successful getAuthToken() using a future with customTokens=false without
     * expiring tokens.
     */
    public void testGetAuthTokenWithCallback_Options_Handler_DefaultToken_Success()
            throws IOException, AuthenticatorException, OperationCanceledException {
        addAccountExplicitly(ACCOUNT, ACCOUNT_PASSWORD, null);
        final HandlerThread handlerThread = new HandlerThread("accounts.test");
        handlerThread.start();
        TokenFetcher fetcherWithOptions = new TokenFetcher() {
            @Override
            public Bundle fetch(String tokenType)
                    throws OperationCanceledException, AuthenticatorException, IOException {
                final AtomicReference<Bundle> actualRef = new AtomicReference<>();
                final CountDownLatch latch = new CountDownLatch(1);

                AccountManagerCallback<Bundle> callback = new AccountManagerCallback<Bundle>() {
                    @Override
                    public void run(AccountManagerFuture<Bundle> bundleFuture) {
                        Bundle resultBundle = null;
                        try {
                            resultBundle = bundleFuture.getResult();
                            actualRef.set(resultBundle);
                        } catch (OperationCanceledException e) {
                            fail("should not throw an OperationCanceledException");
                        } catch (IOException e) {
                            fail("should not throw an IOException");
                        } catch (AuthenticatorException e) {
                            fail("should not throw an AuthenticatorException");
                        } finally {
                            latch.countDown();
                        }
                    }
                };

                am.getAuthToken(getAccount(),
                        tokenType,
                        OPTIONS_BUNDLE,
                        false /* no failure notification */,
                        callback,
                        new Handler(handlerThread.getLooper()));

                // Wait with timeout for the callback to do its work
                try {
                    latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    fail("should not throw an InterruptedException");
                }
                return actualRef.get();
            }

            @Override
            public Account getAccount() {
                return ACCOUNT;
            }
        };
        validateSuccessfulTokenFetchingLifecycle(fetcherWithOptions, AUTH_TOKEN_TYPE);
        validateSuccessfulTokenFetchingLifecycle(fetcherWithOptions, AUTH_EXPIRING_TOKEN_TYPE);
    }

    /**
     * Test successful getAuthToken() using a future with customTokens=false without
     * expiring tokens.
     */
    public void testGetAuthTokenWithCallback_Options_Handler_CustomToken_Success()
            throws IOException, AuthenticatorException, OperationCanceledException {
        addAccountExplicitly(CUSTOM_TOKEN_ACCOUNT, ACCOUNT_PASSWORD, null);
        final HandlerThread handlerThread = new HandlerThread("accounts.test");
        handlerThread.start();
        TokenFetcher fetcherWithOptions = new TokenFetcher() {
            @Override
            public Bundle fetch(String tokenType)
                    throws OperationCanceledException, AuthenticatorException, IOException {
                final AtomicReference<Bundle> actualRef = new AtomicReference<>();
                final CountDownLatch latch = new CountDownLatch(1);

                AccountManagerCallback<Bundle> callback = new AccountManagerCallback<Bundle>() {
                    @Override
                    public void run(AccountManagerFuture<Bundle> bundleFuture) {
                        Bundle resultBundle = null;
                        try {
                            resultBundle = bundleFuture.getResult();
                            actualRef.set(resultBundle);
                        } catch (OperationCanceledException e) {
                            fail("should not throw an OperationCanceledException");
                        } catch (IOException e) {
                            fail("should not throw an IOException");
                        } catch (AuthenticatorException e) {
                            fail("should not throw an AuthenticatorException");
                        } finally {
                            latch.countDown();
                        }
                    }
                };

                am.getAuthToken(getAccount(),
                        tokenType,
                        OPTIONS_BUNDLE,
                        false /* no failure notification */,
                        callback,
                        new Handler(handlerThread.getLooper()));

                // Wait with timeout for the callback to do its work
                try {
                    latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    fail("should not throw an InterruptedException");
                }
                return actualRef.get();
            }

            @Override
            public Account getAccount() {
                return CUSTOM_TOKEN_ACCOUNT;
            }
        };
        validateSuccessfulTokenFetchingLifecycle(fetcherWithOptions, AUTH_TOKEN_TYPE);
        validateSuccessfulTokenFetchingLifecycle(fetcherWithOptions, AUTH_EXPIRING_TOKEN_TYPE);
    }

    /**
     * Test getAuthToken() with callback and handler
     */
    public void testGetAuthTokenWithCallbackAndHandler() throws IOException, AuthenticatorException,
            OperationCanceledException {

        addAccountExplicitly(ACCOUNT, ACCOUNT_PASSWORD, null /* userData */);

        testGetAuthTokenWithCallbackAndHandler(null /* handler */);
        testGetAuthTokenWithCallbackAndHandler(new Handler(Looper.getMainLooper()));
    }

    private void testGetAuthTokenWithCallbackAndHandler(Handler handler) throws IOException,
            AuthenticatorException, OperationCanceledException {

        final CountDownLatch latch = new CountDownLatch(1);

        AccountManagerCallback<Bundle> callback = new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> bundleFuture) {

                Bundle resultBundle = null;
                try {
                    resultBundle = bundleFuture.getResult();

                    // Assert returned result
                    validateAccountAndAuthTokenResult(resultBundle);

                } catch (OperationCanceledException e) {
                    fail("should not throw an OperationCanceledException");
                } catch (IOException e) {
                    fail("should not throw an IOException");
                } catch (AuthenticatorException e) {
                    fail("should not throw an AuthenticatorException");
                }
                finally {
                    latch.countDown();
                }
            }
        };

        AccountManagerFuture<Bundle> futureBundle = am.getAuthToken(ACCOUNT,
                AUTH_TOKEN_TYPE,
                false /* no failure notification */,
                callback,
                handler
        );

        Bundle resultBundle = futureBundle.getResult();

        assertTrue(futureBundle.isDone());
        assertNotNull(resultBundle);

        // Wait with timeout for the callback to do its work
        try {
            latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }
    }

    /**
     * test getAuthToken() with options and callback and handler
     */
    public void testGetAuthTokenWithOptionsAndCallback() throws IOException,
            AuthenticatorException, OperationCanceledException {

        addAccountExplicitly(ACCOUNT, ACCOUNT_PASSWORD, null /* userData */);

        testGetAuthTokenWithOptionsAndCallbackAndHandler(null /* handler */);
        testGetAuthTokenWithOptionsAndCallbackAndHandler(new Handler(Looper.getMainLooper()));
    }

    private void testGetAuthTokenWithOptionsAndCallbackAndHandler(Handler handler) throws
            IOException, AuthenticatorException, OperationCanceledException {

        final CountDownLatch latch = new CountDownLatch(1);

        AccountManagerCallback<Bundle> callback = new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> bundleFuture) {

                Bundle resultBundle = null;
                try {
                    resultBundle = bundleFuture.getResult();
                    // Assert returned result
                    validateAccountAndAuthTokenResult(resultBundle);
                } catch (OperationCanceledException e) {
                    fail("should not throw an OperationCanceledException");
                } catch (IOException e) {
                    fail("should not throw an IOException");
                } catch (AuthenticatorException e) {
                    fail("should not throw an AuthenticatorException");
                }
                finally {
                    latch.countDown();
                }
            }
        };

        AccountManagerFuture<Bundle> futureBundle = am.getAuthToken(ACCOUNT,
                AUTH_TOKEN_TYPE,
                OPTIONS_BUNDLE,
                mActivity,
                callback,
                handler
        );

        Bundle resultBundle = futureBundle.getResult();

        assertTrue(futureBundle.isDone());
        assertNotNull(resultBundle);

        // Wait with timeout for the callback to do its work
        try {
            latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }
    }

    /**
     * Test getAuthTokenByFeatures()
     */
    public void testGetAuthTokenByFeatures() throws IOException, AuthenticatorException,
            OperationCanceledException {

        addAccountExplicitly(ACCOUNT, ACCOUNT_PASSWORD, null /* userData */);

        Bundle resultBundle = getAuthTokenByFeature(
                new String[] { NON_EXISTING_FEATURE },
                null /* activity */
        );

        // Assert returned result
        validateNullResult(resultBundle);

        validateOptions(null, mockAuthenticator.mOptionsAddAccount);
        validateOptions(null, mockAuthenticator.mOptionsUpdateCredentials);
        validateOptions(null, mockAuthenticator.mOptionsConfirmCredentials);
        validateOptions(null, mockAuthenticator.mOptionsGetAuthToken);

        mockAuthenticator.clearData();

        // Now test with existing features and an activity
        resultBundle = getAuthTokenByFeature(
                new String[] { NON_EXISTING_FEATURE },
                mActivity
        );

        // Assert returned result
        validateAccountAndAuthTokenResult(resultBundle);

        validateOptions(OPTIONS_BUNDLE, mockAuthenticator.mOptionsAddAccount);
        validateOptions(null, mockAuthenticator.mOptionsUpdateCredentials);
        validateOptions(null, mockAuthenticator.mOptionsConfirmCredentials);
        validateOptions(null, mockAuthenticator.mOptionsGetAuthToken);

        mockAuthenticator.clearData();

        // Now test with existing features and no activity
        resultBundle = getAuthTokenByFeature(
                REQUIRED_FEATURES,
                null /* activity */
        );

        // Assert returned result
        validateAccountAndAuthTokenResult(resultBundle);

        validateOptions(null, mockAuthenticator.mOptionsAddAccount);
        validateOptions(null, mockAuthenticator.mOptionsUpdateCredentials);
        validateOptions(null, mockAuthenticator.mOptionsConfirmCredentials);
        validateOptions(null, mockAuthenticator.mOptionsGetAuthToken);

        mockAuthenticator.clearData();

        // Now test with existing features and an activity
        resultBundle = getAuthTokenByFeature(
                REQUIRED_FEATURES,
                mActivity
        );

        // Assert returned result
        validateAccountAndAuthTokenResult(resultBundle);

        validateOptions(null, mockAuthenticator.mOptionsAddAccount);
        validateOptions(null, mockAuthenticator.mOptionsUpdateCredentials);
        validateOptions(null, mockAuthenticator.mOptionsConfirmCredentials);
        validateOptions(null, mockAuthenticator.mOptionsGetAuthToken);
    }

    /**
     * Test confirmCredentials()
     */
    public void testConfirmCredentials() throws IOException, AuthenticatorException,
            OperationCanceledException {

        addAccountExplicitly(ACCOUNT, ACCOUNT_PASSWORD, null /* userData */);

        AccountManagerFuture<Bundle> futureBundle = am.confirmCredentials(ACCOUNT,
                OPTIONS_BUNDLE,
                mActivity,
                null /* callback*/,
                null /* handler */);

        futureBundle.getResult();

        // Assert returned result
        validateCredentials();
    }

    /**
     * Tests the setting of lastAuthenticatedTime on adding account
     */
    public void testLastAuthenticatedTimeAfterAddAccount() throws IOException,
            AuthenticatorException, OperationCanceledException {
        assertTrue(addAccountAndReturnAccountAddedTime(ACCOUNT, ACCOUNT_PASSWORD) > 0);
    }

    /**
     * Test confirmCredentials() for account not on device. Just that no error
     * should be thrown.
     */
    public void testConfirmCredentialsAccountNotOnDevice() throws IOException,
            AuthenticatorException, OperationCanceledException {

        Account account = new Account("AccountNotOnThisDevice", ACCOUNT_TYPE);
        AccountManagerFuture<Bundle> futureBundle = am.confirmCredentials(account,
                OPTIONS_BUNDLE,
                mActivity,
                null /* callback */,
                null /* handler */);

        futureBundle.getResult();
    }

    /**
     * Tests the setting of lastAuthenticatedTime on confirmCredentials being
     * successful.
     */
    public void testLastAuthenticatedTimeAfterConfirmCredentialsSuccess() throws IOException,
            AuthenticatorException, OperationCanceledException {

        long accountAddTime = addAccountAndReturnAccountAddedTime(ACCOUNT, ACCOUNT_PASSWORD);

        // Now this confirm credentials call returns true, which in turn
        // should update the last authenticated timestamp.
        Bundle result = am.confirmCredentials(ACCOUNT,
                OPTIONS_BUNDLE, /* options */
                null, /* activity */
                null /* callback */,
                null /* handler */).getResult();
        long confirmedCredTime = result.getLong(
                AccountManager.KEY_LAST_AUTHENTICATED_TIME, -1);
        assertTrue(confirmedCredTime > accountAddTime);
    }

    /**
     * Tests the setting of lastAuthenticatedTime on updateCredentials being
     * successful.
     */
    public void testLastAuthenticatedTimeAfterUpdateCredentialsSuccess() throws IOException,
            AuthenticatorException, OperationCanceledException {

        long accountAddTime = addAccountAndReturnAccountAddedTime(ACCOUNT, ACCOUNT_PASSWORD);

        am.updateCredentials(ACCOUNT,
                AUTH_TOKEN_TYPE,
                OPTIONS_BUNDLE,
                mActivity,
                null /* callback */,
                null /* handler */).getResult();
        long updateCredTime = getLastAuthenticatedTime(ACCOUNT);
        assertTrue(updateCredTime > accountAddTime);
    }

    /**
     * LastAuthenticatedTime on setPassword should not be disturbed.
     */
    public void testLastAuthenticatedTimeAfterSetPassword() throws IOException,
            AuthenticatorException, OperationCanceledException {
        long accountAddTime = addAccountAndReturnAccountAddedTime(ACCOUNT, ACCOUNT_PASSWORD);
        mockAuthenticator.callSetPassword();
        long setPasswordTime = getLastAuthenticatedTime(ACCOUNT);
        assertTrue(setPasswordTime == accountAddTime);
    }

    /**
     * Test confirmCredentials() with callback
     */
    public void testConfirmCredentialsWithCallbackAndHandler() {

        addAccountExplicitly(ACCOUNT, ACCOUNT_PASSWORD, null /* userData */);

        testConfirmCredentialsWithCallbackAndHandler(null /* handler */);
        testConfirmCredentialsWithCallbackAndHandler(new Handler(Looper.getMainLooper()));
    }

    private void testConfirmCredentialsWithCallbackAndHandler(Handler handler) {
        final CountDownLatch latch = new CountDownLatch(1);
        AccountManagerCallback<Bundle> callback = new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> bundleFuture) {

                Bundle resultBundle = null;
                try {
                    resultBundle = bundleFuture.getResult();

                    // Assert returned result
                    validateCredentials();

                    assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
                } catch (OperationCanceledException e) {
                    fail("should not throw an OperationCanceledException");
                } catch (IOException e) {
                    fail("should not throw an IOException");
                } catch (AuthenticatorException e) {
                    fail("should not throw an AuthenticatorException");
                }
                finally {
                    latch.countDown();
                }
            }
        };
        AccountManagerFuture<Bundle> futureBundle = am.confirmCredentials(ACCOUNT,
                OPTIONS_BUNDLE,
                mActivity,
                callback,
                handler);
        // Wait with timeout for the callback to do its work
        try {
            latch.await(3 * LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }
    }

    /**
     * Test updateCredentials()
     */
    public void testUpdateCredentials() throws IOException, AuthenticatorException,
            OperationCanceledException {

        addAccountExplicitly(ACCOUNT, ACCOUNT_PASSWORD, null /* userData */);

        AccountManagerFuture<Bundle> futureBundle = am.updateCredentials(ACCOUNT,
                AUTH_TOKEN_TYPE,
                OPTIONS_BUNDLE,
                mActivity,
                null /* callback*/,
                null /* handler */);

        Bundle result = futureBundle.getResult();

        validateAccountAndNoAuthTokenResult(result);

        // Assert returned result
        validateCredentials();
    }

    /**
     * Test updateCredentials() with callback and handler
     */
    public void testUpdateCredentialsWithCallbackAndHandler() throws IOException,
            AuthenticatorException, OperationCanceledException {

        addAccountExplicitly(ACCOUNT, ACCOUNT_PASSWORD, null /* userData */);

        testUpdateCredentialsWithCallbackAndHandler(null /* handler */);
        testUpdateCredentialsWithCallbackAndHandler(new Handler(Looper.getMainLooper()));
    }

    private void testUpdateCredentialsWithCallbackAndHandler(Handler handler) throws IOException,
            AuthenticatorException, OperationCanceledException {

        final CountDownLatch latch = new CountDownLatch(1);

        AccountManagerCallback<Bundle> callback = new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> bundleFuture) {

                Bundle resultBundle = null;
                try {
                    resultBundle = bundleFuture.getResult();

                    // Assert returned result
                    validateCredentials();
                    assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));

                } catch (OperationCanceledException e) {
                    fail("should not throw an OperationCanceledException");
                } catch (IOException e) {
                    fail("should not throw an IOException");
                } catch (AuthenticatorException e) {
                    fail("should not throw an AuthenticatorException");
                }
                finally {
                    latch.countDown();
                }
            }
        };

        AccountManagerFuture<Bundle> futureBundle = am.updateCredentials(ACCOUNT,
                AUTH_TOKEN_TYPE,
                OPTIONS_BUNDLE,
                mActivity,
                callback,
                handler);

        futureBundle.getResult();

        // Wait with timeout for the callback to do its work
        try {
            latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }
    }

    /**
     * Test editProperties()
     */
    public void testEditProperties() throws IOException, AuthenticatorException,
            OperationCanceledException {

        AccountManagerFuture<Bundle> futureBundle = am.editProperties(ACCOUNT_TYPE,
                mActivity,
                null /* callback */,
                null /* handler*/);

        Bundle result = futureBundle.getResult();

        validateAccountAndNoAuthTokenResult(result);

        // Assert returned result
        assertEquals(ACCOUNT_TYPE, mockAuthenticator.getAccountType());
    }

    /**
     * Test editProperties() with callback and handler
     */
    public void testEditPropertiesWithCallbackAndHandler() {
        testEditPropertiesWithCallbackAndHandler(null /* handler */);
        testEditPropertiesWithCallbackAndHandler(new Handler(Looper.getMainLooper()));
    }

    private void testEditPropertiesWithCallbackAndHandler(Handler handler) {
        final CountDownLatch latch = new CountDownLatch(1);

        AccountManagerCallback<Bundle> callback = new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> bundleFuture) {
                try {
                    // Assert returned result
                    assertEquals(ACCOUNT_TYPE, mockAuthenticator.getAccountType());
                }
                finally {
                    latch.countDown();
                }
            }
        };

        AccountManagerFuture<Bundle> futureBundle = am.editProperties(ACCOUNT_TYPE,
                mActivity,
                callback,
                handler);

        // Wait with timeout for the callback to do its work
        try {
            latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }
    }

    /**
     * Test addOnAccountsUpdatedListener() with handler
     */
    public void testAddOnAccountsUpdatedListenerWithHandler() throws IOException,
            AuthenticatorException, OperationCanceledException {

        testAddOnAccountsUpdatedListenerWithHandler(null /* handler */,
                false /* updateImmediately */);

        // Need to cleanup intermediate state
        assertTrue(removeAccount(am, ACCOUNT, mActivity, null /* callback */).getBoolean(
                AccountManager.KEY_BOOLEAN_RESULT));

        testAddOnAccountsUpdatedListenerWithHandler(null /* handler */,
                true /* updateImmediately */);

        // Need to cleanup intermediate state
        assertTrue(removeAccount(am, ACCOUNT, mActivity, null /* callback */).getBoolean(
                AccountManager.KEY_BOOLEAN_RESULT));

        testAddOnAccountsUpdatedListenerWithHandler(new Handler(Looper.getMainLooper()),
                false /* updateImmediately */);

        // Need to cleanup intermediate state
        assertTrue(removeAccount(am, ACCOUNT, mActivity, null /* callback */).getBoolean(
                AccountManager.KEY_BOOLEAN_RESULT));

        testAddOnAccountsUpdatedListenerWithHandler(new Handler(Looper.getMainLooper()),
                true /* updateImmediately */);
    }

    private void testAddOnAccountsUpdatedListenerWithHandler(Handler handler,
            boolean updateImmediately) {

        final CountDownLatch latch = new CountDownLatch(1);

        OnAccountsUpdateListener listener = new OnAccountsUpdateListener() {
            @Override
            public void onAccountsUpdated(Account[] accounts) {
                latch.countDown();
            }
        };

        // Add a listener
        am.addOnAccountsUpdatedListener(listener,
                handler,
                updateImmediately);

        addAccountExplicitly(ACCOUNT, ACCOUNT_PASSWORD, null /* userData */);

        // Wait with timeout for the callback to do its work
        try {
            latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }

        // Cleanup
        am.removeOnAccountsUpdatedListener(listener);
    }

    /**
     * Test removeOnAccountsUpdatedListener() with handler
     */
    public void testRemoveOnAccountsUpdatedListener() throws IOException, AuthenticatorException,
            OperationCanceledException {

        testRemoveOnAccountsUpdatedListenerWithHandler(null /* handler */);

        // Need to cleanup intermediate state
        assertTrue(removeAccount(am, ACCOUNT, mActivity, null /* callback */).getBoolean(
                AccountManager.KEY_BOOLEAN_RESULT));

        testRemoveOnAccountsUpdatedListenerWithHandler(new Handler(Looper.getMainLooper()));
    }

    private void testRemoveOnAccountsUpdatedListenerWithHandler(Handler handler) {
        final CountDownLatch latch = new CountDownLatch(1);

        OnAccountsUpdateListener listener = new OnAccountsUpdateListener() {
            @Override
            public void onAccountsUpdated(Account[] accounts) {
                fail("should not be called");
            }
        };

        // First add a listener
        am.addOnAccountsUpdatedListener(listener,
                handler,
                false /* updateImmediately */);

        // Then remove the listener
        am.removeOnAccountsUpdatedListener(listener);

        addAccountExplicitly(ACCOUNT, ACCOUNT_PASSWORD, null /* userData */);

        // Wait with timeout for the callback to do its work
        try {
            latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }
    }

    /**
     * Test hasFeature
     */
    public void testHasFeature()
            throws IOException, AuthenticatorException, OperationCanceledException {

        assertHasFeature(null /* handler */);
        assertHasFeature(new Handler(Looper.getMainLooper()));

        assertHasFeatureWithCallback(null /* handler */);
        assertHasFeatureWithCallback(new Handler(Looper.getMainLooper()));
    }

    private void assertHasFeature(Handler handler)
            throws IOException, AuthenticatorException, OperationCanceledException {
        Bundle resultBundle = addAccount(am,
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                REQUIRED_FEATURES,
                OPTIONS_BUNDLE,
                mActivity,
                null /* callback */,
                null /* handler */);

        // Assert parameters has been passed correctly
        validateAccountAndAuthTokenType();
        validateFeatures();

        AccountManagerFuture<Boolean> booleanFuture = am.hasFeatures(ACCOUNT,
                new String[]{FEATURE_1},
                null /* callback */,
                handler);
        assertTrue(booleanFuture.getResult());

        booleanFuture = am.hasFeatures(ACCOUNT,
                new String[]{FEATURE_2},
                null /* callback */,
                handler);
        assertTrue(booleanFuture.getResult());

        booleanFuture = am.hasFeatures(ACCOUNT,
                new String[]{FEATURE_1, FEATURE_2},
                null /* callback */,
                handler);
        assertTrue(booleanFuture.getResult());

        booleanFuture = am.hasFeatures(ACCOUNT,
                new String[]{NON_EXISTING_FEATURE},
                null /* callback */,
                handler);
        assertFalse(booleanFuture.getResult());

        booleanFuture = am.hasFeatures(ACCOUNT,
                new String[]{NON_EXISTING_FEATURE, FEATURE_1},
                null /* callback */,
                handler);
        assertFalse(booleanFuture.getResult());

        booleanFuture = am.hasFeatures(ACCOUNT,
                new String[]{NON_EXISTING_FEATURE, FEATURE_1, FEATURE_2},
                null /* callback */,
                handler);
        assertFalse(booleanFuture.getResult());
    }

    private AccountManagerCallback<Boolean> getAssertTrueCallback(final CountDownLatch latch) {
        return new AccountManagerCallback<Boolean>() {
            @Override
            public void run(AccountManagerFuture<Boolean> booleanFuture) {
                try {
                    // Assert returned result should be TRUE
                    assertTrue(booleanFuture.getResult());
                } catch (Exception e) {
                    fail("Exception: " + e);
                } finally {
                    latch.countDown();
                }
            }
        };
    }

    private AccountManagerCallback<Boolean> getAssertFalseCallback(final CountDownLatch latch) {
        return new AccountManagerCallback<Boolean>() {
            @Override
            public void run(AccountManagerFuture<Boolean> booleanFuture) {
                try {
                    // Assert returned result should be FALSE
                    assertFalse(booleanFuture.getResult());
                } catch (Exception e) {
                    fail("Exception: " + e);
                } finally {
                    latch.countDown();
                }
            }
        };
    }

    private void waitForLatch(final CountDownLatch latch) {
        // Wait with timeout for the callback to do its work
        try {
            latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }
    }

    private void assertHasFeatureWithCallback(Handler handler)
            throws IOException, AuthenticatorException, OperationCanceledException {
        Bundle resultBundle = addAccount(am,
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                REQUIRED_FEATURES,
                OPTIONS_BUNDLE,
                mActivity,
                null /* callback */,
                null /* handler */);

        // Assert parameters has been passed correctly
        validateAccountAndAuthTokenType();
        validateFeatures();

        CountDownLatch latch = new CountDownLatch(1);
        am.hasFeatures(ACCOUNT,
                new String[]{FEATURE_1},
                getAssertTrueCallback(latch),
                handler);
        waitForLatch(latch);

        latch = new CountDownLatch(1);
        am.hasFeatures(ACCOUNT,
                new String[]{FEATURE_2},
                getAssertTrueCallback(latch),
                handler);
        waitForLatch(latch);

        latch = new CountDownLatch(1);
        am.hasFeatures(ACCOUNT,
                new String[]{FEATURE_1, FEATURE_2},
                getAssertTrueCallback(latch),
                handler);
        waitForLatch(latch);

        latch = new CountDownLatch(1);
        am.hasFeatures(ACCOUNT,
                new String[]{NON_EXISTING_FEATURE},
                getAssertFalseCallback(latch),
                handler);
        waitForLatch(latch);

        latch = new CountDownLatch(1);
        am.hasFeatures(ACCOUNT,
                new String[]{NON_EXISTING_FEATURE, FEATURE_1},
                getAssertFalseCallback(latch),
                handler);
        waitForLatch(latch);

        latch = new CountDownLatch(1);
        am.hasFeatures(ACCOUNT,
                new String[]{NON_EXISTING_FEATURE, FEATURE_1, FEATURE_2},
                getAssertFalseCallback(latch),
                handler);
        waitForLatch(latch);
    }

    private long getLastAuthenticatedTime(Account account) throws OperationCanceledException,
            AuthenticatorException, IOException {
        Bundle options = new Bundle();
        options.putBoolean(MockAccountAuthenticator.KEY_RETURN_INTENT, true);
        // Not really confirming, but a way to get last authenticated timestamp
        Bundle result = am.confirmCredentials(account,
                options,// OPTIONS_BUNDLE,
                null, /* activity */
                null /* callback */,
                null /* handler */).getResult();
        return result.getLong(
                AccountManager.KEY_LAST_AUTHENTICATED_TIME, -1);
    }

    private long addAccountAndReturnAccountAddedTime(Account account, String password)
            throws OperationCanceledException, AuthenticatorException, IOException {
        addAccount(am,
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                REQUIRED_FEATURES,
                OPTIONS_BUNDLE,
                mActivity,
                null /* callback */,
                null /* handler */);
        return getLastAuthenticatedTime(account);
    }

    /**
     * Tests that AccountManagerService is properly caching data.
     */
    public void testGetsAreCached() {

        // Add an account,
        assertEquals(false, isAccountPresent(am.getAccounts(), ACCOUNT));
        addAccountExplicitly(ACCOUNT, ACCOUNT_PASSWORD, null /* userData */);

        // Then verify that we don't hit disk retrieving it,
        StrictMode.ThreadPolicy oldPolicy = StrictMode.getThreadPolicy();
        try {
            StrictMode.setThreadPolicy(
                new StrictMode.ThreadPolicy.Builder().detectDiskReads().penaltyDeath().build());
            Account[] accounts = am.getAccounts();
            assertNotNull(accounts);
            assertTrue(accounts.length > 0);
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    /**
     * Tests a basic startAddAccountSession() which returns a bundle containing
     * encrypted session bundle, account password and status token.
     */
    public void testStartAddAccountSession()
            throws IOException, AuthenticatorException, OperationCanceledException {
        Bundle options = new Bundle();
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, getSessionBundle(null));
        options.putAll(OPTIONS_BUNDLE);

        Bundle resultBundle = startAddAccountSession(
                am,
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                REQUIRED_FEATURES,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        // Assert parameters has been passed correctly
        validateAccountAndAuthTokenType();
        validateFeatures();

        validateOptions(options, mockAuthenticator.mOptionsStartAddAccountSession);
        assertNotNull(mockAuthenticator.mOptionsStartAddAccountSession);
        assertEquals(accountName, mockAuthenticator.mOptionsStartAddAccountSession
                .getString(Fixtures.KEY_ACCOUNT_NAME));

        validateSystemOptions(mockAuthenticator.mOptionsStartAddAccountSession);
        validateOptions(null, mockAuthenticator.mOptionsUpdateCredentials);
        validateOptions(null, mockAuthenticator.mOptionsConfirmCredentials);
        validateOptions(null, mockAuthenticator.mOptionsGetAuthToken);
        validateOptions(null, mockAuthenticator.mOptionsAddAccount);

        // Assert returned result
        // Assert that auth token was stripped.
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
        validateSessionBundleAndPasswordAndStatusTokenResult(resultBundle);
    }

    /**
     * Tests startAddAccountSession() with null session bundle. Only account
     * password and status token should be included in the result as session
     * bundle is not inspected.
     */
    public void testStartAddAccountSessionWithNullSessionBundle()
            throws IOException, AuthenticatorException, OperationCanceledException {
        Bundle options = new Bundle();
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putAll(OPTIONS_BUNDLE);

        Bundle resultBundle = startAddAccountSession(
                am,
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                REQUIRED_FEATURES,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        // Assert parameters has been passed correctly
        validateAccountAndAuthTokenType();
        validateFeatures();

        validateOptions(options, mockAuthenticator.mOptionsStartAddAccountSession);
        assertNotNull(mockAuthenticator.mOptionsStartAddAccountSession);
        assertEquals(accountName, mockAuthenticator.mOptionsStartAddAccountSession
                .getString(Fixtures.KEY_ACCOUNT_NAME));

        validateSystemOptions(mockAuthenticator.mOptionsStartAddAccountSession);
        validateOptions(null, mockAuthenticator.mOptionsUpdateCredentials);
        validateOptions(null, mockAuthenticator.mOptionsConfirmCredentials);
        validateOptions(null, mockAuthenticator.mOptionsGetAuthToken);
        validateOptions(null, mockAuthenticator.mOptionsAddAccount);

        // Assert returned result
        // Assert that auth token was stripped.
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
        assertNull(resultBundle.getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE));
        assertEquals(ACCOUNT_PASSWORD, resultBundle.getString(AccountManager.KEY_PASSWORD));
        assertEquals(ACCOUNT_STATUS_TOKEN,
                resultBundle.getString(AccountManager.KEY_ACCOUNT_STATUS_TOKEN));
    }

    /**
     * Tests startAddAccountSession() with empty session bundle. An encrypted
     * session bundle, account password and status token should be included in
     * the result as session bundle is not inspected.
     */
    public void testStartAddAccountSessionWithEmptySessionBundle()
            throws IOException, AuthenticatorException, OperationCanceledException {
        Bundle options = new Bundle();
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, new Bundle());
        options.putAll(OPTIONS_BUNDLE);

        Bundle resultBundle = startAddAccountSession(
                am,
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                REQUIRED_FEATURES,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        // Assert parameters has been passed correctly
        validateAccountAndAuthTokenType();
        validateFeatures();

        validateOptions(options, mockAuthenticator.mOptionsStartAddAccountSession);
        assertNotNull(mockAuthenticator.mOptionsStartAddAccountSession);
        assertEquals(accountName, mockAuthenticator.mOptionsStartAddAccountSession
                .getString(Fixtures.KEY_ACCOUNT_NAME));

        validateSystemOptions(mockAuthenticator.mOptionsStartAddAccountSession);
        validateOptions(null, mockAuthenticator.mOptionsUpdateCredentials);
        validateOptions(null, mockAuthenticator.mOptionsConfirmCredentials);
        validateOptions(null, mockAuthenticator.mOptionsGetAuthToken);
        validateOptions(null, mockAuthenticator.mOptionsAddAccount);

        // Assert returned result
        // Assert that auth token was stripped.
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
        validateSessionBundleAndPasswordAndStatusTokenResult(resultBundle);
    }

    /**
     * Tests startAddAccountSession with authenticator activity started. When
     * Activity is provided, AccountManager would start the resolution Intent
     * and return the final result which contains an encrypted session bundle,
     * account password and status token.
     */
    public void testStartAddAccountSessionIntervene()
            throws IOException, AuthenticatorException, OperationCanceledException {
        Bundle options = new Bundle();
        String accountName = Fixtures.PREFIX_NAME_INTERVENE + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, getSessionBundle(null));
        options.putAll(OPTIONS_BUNDLE);

        Bundle resultBundle = startAddAccountSession(
                am,
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                REQUIRED_FEATURES,
                options,
                mActivity,
                null /* callback */,
                null /* handler */);

        // Assert parameters has been passed correctly
        validateAccountAndAuthTokenType();
        validateFeatures();

        validateStartAddAccountSessionOptions(accountName, options);

        // Assert returned result
        assertNull(resultBundle.getParcelable(AccountManager.KEY_INTENT));
        // Assert that auth token was stripped.
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
        validateSessionBundleAndPasswordAndStatusTokenResult(resultBundle);
    }

    /**
     * Tests startAddAccountSession with KEY_INTENT returned but not started
     * automatically. When no Activity is provided and authenticator requires
     * additional data from user, KEY_INTENT will be returned by AccountManager.
     */
    public void testStartAddAccountSessionWithReturnIntent()
            throws IOException, AuthenticatorException, OperationCanceledException {
        Bundle options = new Bundle();
        String accountName = Fixtures.PREFIX_NAME_INTERVENE + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, getSessionBundle(null));
        options.putAll(OPTIONS_BUNDLE);

        Bundle resultBundle = startAddAccountSession(
                am,
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                REQUIRED_FEATURES,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        // Assert parameters has been passed correctly
        validateAccountAndAuthTokenType();
        validateFeatures();

        validateStartAddAccountSessionOptions(accountName, options);

        // Assert returned result
        Intent returnIntent = resultBundle.getParcelable(AccountManager.KEY_INTENT);
        // Assert that KEY_INTENT is returned.
        assertNotNull(returnIntent);
        assertNotNull(returnIntent.getParcelableExtra(Fixtures.KEY_RESULT));
        // Assert that no other data is returned.
        assertNull(resultBundle.getString(AccountManager.KEY_ACCOUNT_STATUS_TOKEN));
        assertNull(resultBundle.getString(AccountManager.KEY_PASSWORD));
        assertNull(resultBundle.getString(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE));
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
    }

    /**
     * Tests startAddAccountSession error case. AuthenticatorException is
     * expected when authenticator return
     * {@link AccountManager#ERROR_CODE_INVALID_RESPONSE} error code.
     */
    public void testStartAddAccountSessionError() throws IOException, OperationCanceledException {
        Bundle options = new Bundle();
        String accountName = Fixtures.PREFIX_NAME_ERROR + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, getSessionBundle(null));
        options.putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_INVALID_RESPONSE);
        options.putString(AccountManager.KEY_ERROR_MESSAGE, ERROR_MESSAGE);
        options.putAll(OPTIONS_BUNDLE);

        try {
            startAddAccountSession(
                    am,
                    ACCOUNT_TYPE,
                    AUTH_TOKEN_TYPE,
                    REQUIRED_FEATURES,
                    options,
                    null /* activity */,
                    null /* callback */,
                    null /* handler */);
            fail("startAddAccountSession should throw AuthenticatorException in error case.");
        } catch (AuthenticatorException e) {
        }
    }

    /**
     * Tests startAddAccountSession() with callback and handler. An encrypted
     * session bundle, account password and status token should be included in
     * the result. Callback should be triggered with the result regardless of a
     * handler is provider or not.
     */
    public void testStartAddAccountSessionWithCallbackAndHandler()
            throws IOException, AuthenticatorException, OperationCanceledException {
        testStartAddAccountSessionWithCallbackAndHandler(null /* handler */);
        testStartAddAccountSessionWithCallbackAndHandler(new Handler(Looper.getMainLooper()));
    }

    /**
     * Tests startAddAccountSession() with callback and handler and activity
     * started. When Activity is provided, AccountManager would start the
     * resolution Intent and return the final result which contains an encrypted
     * session bundle, account password and status token. Callback should be
     * triggered with the result regardless of a handler is provider or not.
     */
    public void testStartAddAccountSessionWithCallbackAndHandlerWithIntervene()
            throws IOException, AuthenticatorException, OperationCanceledException {
        testStartAddAccountSessionWithCallbackAndHandlerWithIntervene(null /* handler */);
        testStartAddAccountSessionWithCallbackAndHandlerWithIntervene(
                new Handler(Looper.getMainLooper()));
    }

    /**
     * Tests startAddAccountSession() with callback and handler with KEY_INTENT
     * returned. When no Activity is provided and authenticator requires
     * additional data from user, KEY_INTENT will be returned by AccountManager
     * in callback regardless of a handler is provider or not.
     */
    public void testStartAddAccountSessionWithCallbackAndHandlerWithReturnIntent()
            throws IOException, AuthenticatorException, OperationCanceledException {
        testStartAddAccountSessionWithCallbackAndHandlerWithReturnIntent(null /* handler */);
        testStartAddAccountSessionWithCallbackAndHandlerWithReturnIntent(
                new Handler(Looper.getMainLooper()));
    }

    /**
     * Tests startAddAccountSession() error case with callback and handler.
     * AuthenticatorException is expected when authenticator return
     * {@link AccountManager#ERROR_CODE_INVALID_RESPONSE} error code.
     */
    public void testStartAddAccountSessionErrorWithCallbackAndHandler()
            throws IOException, OperationCanceledException {
        testStartAddAccountSessionErrorWithCallbackAndHandler(null /* handler */);
        testStartAddAccountSessionErrorWithCallbackAndHandler(new Handler(Looper.getMainLooper()));
    }

    private void testStartAddAccountSessionWithCallbackAndHandler(Handler handler)
            throws IOException, AuthenticatorException, OperationCanceledException {
        final Bundle options = new Bundle();
        final String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@"
                + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, getSessionBundle(null));
        options.putAll(OPTIONS_BUNDLE);

        final CountDownLatch latch = new CountDownLatch(1);

        AccountManagerCallback<Bundle> callback = new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> bundleFuture) {
                Bundle resultBundle = null;
                try {
                    resultBundle = bundleFuture.getResult();
                } catch (OperationCanceledException e) {
                    fail("should not throw an OperationCanceledException");
                } catch (IOException e) {
                    fail("should not throw an IOException");
                } catch (AuthenticatorException e) {
                    fail("should not throw an AuthenticatorException");
                }

                // Assert parameters has been passed correctly
                validateAccountAndAuthTokenType();
                validateFeatures();

                validateStartAddAccountSessionOptions(accountName, options);

                // Assert returned result
                // Assert that auth token was stripped.
                assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
                validateSessionBundleAndPasswordAndStatusTokenResult(resultBundle);

                latch.countDown();
            }
        };

        startAddAccountSession(
                am,
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                REQUIRED_FEATURES,
                options,
                mActivity,
                callback,
                handler);

        // Wait with timeout for the callback to do its work
        try {
            latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }
    }

    private void testStartAddAccountSessionWithCallbackAndHandlerWithIntervene(Handler handler)
            throws IOException, AuthenticatorException, OperationCanceledException {
        final Bundle options = new Bundle();
        final String accountName = Fixtures.PREFIX_NAME_INTERVENE + "@"
                + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, getSessionBundle(null));
        options.putAll(OPTIONS_BUNDLE);

        final CountDownLatch latch = new CountDownLatch(1);

        AccountManagerCallback<Bundle> callback = new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> bundleFuture) {
                Bundle resultBundle = null;
                try {
                    resultBundle = bundleFuture.getResult();
                } catch (OperationCanceledException e) {
                    fail("should not throw an OperationCanceledException");
                } catch (IOException e) {
                    fail("should not throw an IOException");
                } catch (AuthenticatorException e) {
                    fail("should not throw an AuthenticatorException");
                }

                // Assert parameters has been passed correctly
                validateAccountAndAuthTokenType();
                validateFeatures();

                validateStartAddAccountSessionOptions(accountName, options);

                // Assert returned result
                assertNull(resultBundle.getParcelable(AccountManager.KEY_INTENT));
                // Assert that auth token was stripped.
                assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
                validateSessionBundleAndPasswordAndStatusTokenResult(resultBundle);

                latch.countDown();
            }
        };

        startAddAccountSession(
                am,
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                REQUIRED_FEATURES,
                options,
                mActivity,
                callback,
                handler);

        // Wait with timeout for the callback to do its work
        try {
            latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }
    }

    private void testStartAddAccountSessionWithCallbackAndHandlerWithReturnIntent(Handler handler)
            throws IOException, AuthenticatorException, OperationCanceledException {
        final Bundle options = new Bundle();
        final String accountName = Fixtures.PREFIX_NAME_INTERVENE + "@"
                + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, getSessionBundle(null));
        options.putAll(OPTIONS_BUNDLE);

        final CountDownLatch latch = new CountDownLatch(1);

        AccountManagerCallback<Bundle> callback = new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> bundleFuture) {
                Bundle resultBundle = null;
                try {
                    resultBundle = bundleFuture.getResult();
                } catch (OperationCanceledException e) {
                    fail("should not throw an OperationCanceledException");
                } catch (IOException e) {
                    fail("should not throw an IOException");
                } catch (AuthenticatorException e) {
                    fail("should not throw an AuthenticatorException");
                }

                // Assert parameters has been passed correctly
                validateAccountAndAuthTokenType();
                validateFeatures();

                validateStartAddAccountSessionOptions(accountName, options);

                // Assert returned result
                Intent returnIntent = resultBundle.getParcelable(AccountManager.KEY_INTENT);
                // Assert KEY_INTENT is returned.
                assertNotNull(returnIntent);
                assertNotNull(returnIntent.getParcelableExtra(Fixtures.KEY_RESULT));
                // Assert that no other data is returned.
                assertNull(resultBundle.getString(AccountManager.KEY_ACCOUNT_STATUS_TOKEN));
                assertNull(resultBundle.getString(AccountManager.KEY_PASSWORD));
                assertNull(resultBundle.getString(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE));
                assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));

                latch.countDown();
            }
        };

        startAddAccountSession(
                am,
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                REQUIRED_FEATURES,
                options,
                null /* activity */,
                callback,
                handler);

        // Wait with timeout for the callback to do its work
        try {
            latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }
    }

    private void testStartAddAccountSessionErrorWithCallbackAndHandler(Handler handler)
            throws IOException, OperationCanceledException {
        final Bundle options = new Bundle();
        final String accountName = Fixtures.PREFIX_NAME_ERROR + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, getSessionBundle(null));
        options.putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_INVALID_RESPONSE);
        options.putString(AccountManager.KEY_ERROR_MESSAGE, ERROR_MESSAGE);
        options.putAll(OPTIONS_BUNDLE);

        final CountDownLatch latch = new CountDownLatch(1);

        AccountManagerCallback<Bundle> callback = new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> bundleFuture) {
                try {
                    bundleFuture.getResult();
                    fail("should have thrown an AuthenticatorException");
                } catch (OperationCanceledException e) {
                    fail("should not throw an OperationCanceledException");
                } catch (IOException e) {
                    fail("should not throw an IOException");
                } catch (AuthenticatorException e) {
                    latch.countDown();
                }
            }
        };

        try {
            startAddAccountSession(
                    am,
                    ACCOUNT_TYPE,
                    AUTH_TOKEN_TYPE,
                    REQUIRED_FEATURES,
                    options,
                    mActivity,
                    callback,
                    handler);
            // AuthenticatorException should be thrown when authenticator
            // returns AccountManager.ERROR_CODE_INVALID_RESPONSE.
            fail("should have thrown an AuthenticatorException");
        } catch (AuthenticatorException e1) {
        }

        // Wait with timeout for the callback to do its work
        try {
            latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }
    }

    private Bundle startAddAccountSession(AccountManager am, String accountType,
            String authTokenType, String[] requiredFeatures, Bundle options, Activity activity,
            AccountManagerCallback<Bundle> callback, Handler handler)
                    throws IOException, AuthenticatorException, OperationCanceledException {

        AccountManagerFuture<Bundle> futureBundle = am.startAddAccountSession(
                accountType,
                authTokenType,
                requiredFeatures,
                options,
                activity,
                callback,
                handler);

        Bundle resultBundle = futureBundle.getResult();
        assertTrue(futureBundle.isDone());
        assertNotNull(resultBundle);

        return resultBundle;
    }

    private void validateStartAddAccountSessionOptions(String accountName, Bundle options) {
        validateOptions(options, mockAuthenticator.mOptionsStartAddAccountSession);
        assertNotNull(mockAuthenticator.mOptionsStartAddAccountSession);
        assertEquals(accountName, mockAuthenticator.mOptionsStartAddAccountSession
                .getString(Fixtures.KEY_ACCOUNT_NAME));

        validateSystemOptions(mockAuthenticator.mOptionsStartAddAccountSession);
        validateOptions(null, mockAuthenticator.mOptionsUpdateCredentials);
        validateOptions(null, mockAuthenticator.mOptionsConfirmCredentials);
        validateOptions(null, mockAuthenticator.mOptionsGetAuthToken);
        validateOptions(null, mockAuthenticator.mOptionsAddAccount);
        validateOptions(null, mockAuthenticator.mOptionsStartUpdateCredentialsSession);
        validateOptions(null, mockAuthenticator.mOptionsFinishSession);
    }

    private void validateSessionBundleAndPasswordAndStatusTokenResult(Bundle resultBundle) {
        Bundle sessionBundle = resultBundle.getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);
        assertNotNull(sessionBundle);
        // Assert that session bundle is encrypted and hence data not visible.
        assertNull(sessionBundle.getString(SESSION_DATA_NAME_1));
        assertEquals(ACCOUNT_PASSWORD, resultBundle.getString(AccountManager.KEY_PASSWORD));
        assertEquals(ACCOUNT_STATUS_TOKEN,
                resultBundle.getString(AccountManager.KEY_ACCOUNT_STATUS_TOKEN));
    }

    /**
     * Test a basic startUpdateCredentialsSession() which returns a bundle containing
     * encrypted session bundle, account password and status token.
     */
    public void testStartUpdateCredentialsSession()
            throws IOException, AuthenticatorException, OperationCanceledException {
        Bundle options = new Bundle();
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, getSessionBundle(null));
        options.putAll(OPTIONS_BUNDLE);

        Bundle resultBundle = startUpdateCredentialsSession(
                am,
                ACCOUNT,
                null /* authTokenType */,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        // Assert parameters has been passed correctly
        assertNull(mockAuthenticator.getAuthTokenType());
        assertEquals(ACCOUNT, mockAuthenticator.mAccount);

        validateStartUpdateCredentialsSessionOptions(accountName, options);

        // Assert returned result
        // Assert that auth token was stripped.
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
        validateSessionBundleAndPasswordAndStatusTokenResult(resultBundle);
    }

    /**
     * Tests startUpdateCredentialsSession() with null session bundle. Only account
     * password and status token should be included in the result as session
     * bundle is not inspected.
     */
    public void testStartUpdateCredentialsSessionWithNullSessionBundle()
            throws IOException, AuthenticatorException, OperationCanceledException {
        Bundle options = new Bundle();
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putAll(OPTIONS_BUNDLE);

        Bundle resultBundle = startUpdateCredentialsSession(
                am,
                ACCOUNT,
                AUTH_TOKEN_TYPE,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        // Assert parameters has been passed correctly
        assertEquals(AUTH_TOKEN_TYPE, mockAuthenticator.getAuthTokenType());
        assertEquals(ACCOUNT, mockAuthenticator.mAccount);

        validateOptions(options, mockAuthenticator.mOptionsStartUpdateCredentialsSession);
        assertNotNull(mockAuthenticator.mOptionsStartUpdateCredentialsSession);
        assertEquals(accountName, mockAuthenticator.mOptionsStartUpdateCredentialsSession
                .getString(Fixtures.KEY_ACCOUNT_NAME));

        validateOptions(null, mockAuthenticator.mOptionsUpdateCredentials);
        validateOptions(null, mockAuthenticator.mOptionsConfirmCredentials);
        validateOptions(null, mockAuthenticator.mOptionsGetAuthToken);
        validateOptions(null, mockAuthenticator.mOptionsAddAccount);

        // Assert returned result
        // Assert that auth token was stripped.
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
        assertNull(resultBundle.getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE));
        assertEquals(ACCOUNT_PASSWORD, resultBundle.getString(AccountManager.KEY_PASSWORD));
        assertEquals(ACCOUNT_STATUS_TOKEN,
                resultBundle.getString(AccountManager.KEY_ACCOUNT_STATUS_TOKEN));
    }

    /**
     * Tests startUpdateCredentialsSession() with empty session bundle. An encrypted
     * session bundle, account password and status token should be included in
     * the result as session bundle is not inspected.
     */
    public void testStartUpdateCredentialsSessionWithEmptySessionBundle()
            throws IOException, AuthenticatorException, OperationCanceledException {
        Bundle options = new Bundle();
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, new Bundle());
        options.putAll(OPTIONS_BUNDLE);

        Bundle resultBundle = startUpdateCredentialsSession(
                am,
                ACCOUNT,
                AUTH_TOKEN_TYPE,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        // Assert parameters has been passed correctly
        assertEquals(AUTH_TOKEN_TYPE, mockAuthenticator.getAuthTokenType());
        assertEquals(ACCOUNT, mockAuthenticator.mAccount);

        validateOptions(options, mockAuthenticator.mOptionsStartUpdateCredentialsSession);
        assertNotNull(mockAuthenticator.mOptionsStartUpdateCredentialsSession);
        assertEquals(accountName, mockAuthenticator.mOptionsStartUpdateCredentialsSession
                .getString(Fixtures.KEY_ACCOUNT_NAME));

        validateOptions(null, mockAuthenticator.mOptionsUpdateCredentials);
        validateOptions(null, mockAuthenticator.mOptionsConfirmCredentials);
        validateOptions(null, mockAuthenticator.mOptionsGetAuthToken);
        validateOptions(null, mockAuthenticator.mOptionsAddAccount);

        // Assert returned result
        // Assert that auth token was stripped.
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
        validateSessionBundleAndPasswordAndStatusTokenResult(resultBundle);
    }

    /**
     * Tests startUpdateCredentialsSession with authenticator activity started. When
     * Activity is provided, AccountManager would start the resolution Intent
     * and return the final result which contains an encrypted session bundle,
     * account password and status token.
     */
    public void testStartUpdateCredentialsSessionIntervene()
            throws IOException, AuthenticatorException, OperationCanceledException {
        Bundle options = new Bundle();
        String accountName = Fixtures.PREFIX_NAME_INTERVENE + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, getSessionBundle(null));
        options.putAll(OPTIONS_BUNDLE);

        Bundle resultBundle = startUpdateCredentialsSession(
                am,
                ACCOUNT,
                AUTH_TOKEN_TYPE,
                options,
                mActivity,
                null /* callback */,
                null /* handler */);

        // Assert parameters has been passed correctly
        assertEquals(AUTH_TOKEN_TYPE, mockAuthenticator.getAuthTokenType());
        assertEquals(ACCOUNT, mockAuthenticator.mAccount);

        validateStartUpdateCredentialsSessionOptions(accountName, options);

        // Assert returned result
        assertNull(resultBundle.getParcelable(AccountManager.KEY_INTENT));
        // Assert that auth token was stripped.
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
        validateSessionBundleAndPasswordAndStatusTokenResult(resultBundle);
    }

    /**
     * Tests startUpdateCredentialsSession with KEY_INTENT returned but not
     * started automatically. When no Activity is provided and authenticator requires
     * additional data from user, KEY_INTENT will be returned by AccountManager.
     */
    public void testStartUpdateCredentialsSessionWithReturnIntent()
            throws IOException, AuthenticatorException, OperationCanceledException {
        Bundle options = new Bundle();
        String accountName = Fixtures.PREFIX_NAME_INTERVENE + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, getSessionBundle(null));
        options.putAll(OPTIONS_BUNDLE);

        Bundle resultBundle = startUpdateCredentialsSession(
                am,
                ACCOUNT,
                AUTH_TOKEN_TYPE,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        // Assert parameters has been passed correctly
        assertEquals(AUTH_TOKEN_TYPE, mockAuthenticator.getAuthTokenType());
        assertEquals(ACCOUNT, mockAuthenticator.mAccount);

        validateStartUpdateCredentialsSessionOptions(accountName, options);

        // Assert returned result
        Intent returnIntent = resultBundle.getParcelable(AccountManager.KEY_INTENT);
        // Assert that KEY_INTENT is returned.
        assertNotNull(returnIntent);
        assertNotNull(returnIntent.getParcelableExtra(Fixtures.KEY_RESULT));
        // Assert that no other data is returned.
        assertNull(resultBundle.getString(AccountManager.KEY_ACCOUNT_STATUS_TOKEN));
        assertNull(resultBundle.getString(AccountManager.KEY_PASSWORD));
        assertNull(resultBundle.getString(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE));
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
    }

    /**
     * Tests startUpdateCredentialsSession error case. AuthenticatorException is
     * expected when authenticator return
     * {@link AccountManager#ERROR_CODE_INVALID_RESPONSE} error code.
     */
    public void testStartUpdateCredentialsSessionError()
            throws IOException, OperationCanceledException {
        Bundle options = new Bundle();
        String accountName = Fixtures.PREFIX_NAME_ERROR + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, getSessionBundle(null));
        options.putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_INVALID_RESPONSE);
        options.putString(AccountManager.KEY_ERROR_MESSAGE, ERROR_MESSAGE);
        options.putAll(OPTIONS_BUNDLE);

        try {
            startUpdateCredentialsSession(
                    am,
                    ACCOUNT,
                    AUTH_TOKEN_TYPE,
                    options,
                    null /* activity */,
                    null /* callback */,
                    null /* handler */);
            fail("startUpdateCredentialsSession should throw AuthenticatorException in error.");
        } catch (AuthenticatorException e) {
        }
    }

    /**
     * Tests startUpdateCredentialsSession() with callback and handler. An encrypted
     * session bundle, account password and status token should be included in
     * the result. Callback should be triggered with the result regardless of a
     * handler is provider or not.
     */
    public void testStartUpdateCredentialsSessionWithCallbackAndHandler()
            throws IOException, AuthenticatorException, OperationCanceledException {
        testStartUpdateCredentialsSessionWithCallbackAndHandler(null /* handler */);
        testStartUpdateCredentialsSessionWithCallbackAndHandler(
                new Handler(Looper.getMainLooper()));
    }

    /**
     * Tests startUpdateCredentialsSession() with callback and handler and
     * activity started. When Activity is provided, AccountManager would start the
     * resolution Intent and return the final result which contains an encrypted
     * session bundle, account password and status token. Callback should be
     * triggered with the result regardless of a handler is provider or not.
     */
    public void testStartUpdateCredentialsSessionWithCallbackAndHandlerWithIntervene()
            throws IOException, AuthenticatorException, OperationCanceledException {
        testStartUpdateCredentialsSessionWithCallbackAndHandlerWithIntervene(null /* handler */);
        testStartUpdateCredentialsSessionWithCallbackAndHandlerWithIntervene(
                new Handler(Looper.getMainLooper()));
    }

    /**
     * Tests startUpdateCredentialsSession() with callback and handler with
     * KEY_INTENT returned. When no Activity is provided and authenticator requires
     * additional data from user, KEY_INTENT will be returned by AccountManager
     * in callback regardless of a handler is provider or not.
     */
    public void testStartUpdateCredentialsSessionWithCallbackAndHandlerWithReturnIntent()
            throws IOException, AuthenticatorException, OperationCanceledException {
        testStartUpdateCredentialsSessionWithCallbackAndHandlerWithReturnIntent(null /* handler */);
        testStartUpdateCredentialsSessionWithCallbackAndHandlerWithReturnIntent(
                new Handler(Looper.getMainLooper()));
    }

    /**
     * Tests startUpdateCredentialsSession() error case with callback and
     * handler. AuthenticatorException is expected when authenticator return
     * {@link AccountManager#ERROR_CODE_INVALID_RESPONSE} error code.
     */
    public void testStartUpdateCredentialsSessionErrorWithCallbackAndHandler()
            throws IOException, OperationCanceledException {
        testStartUpdateCredentialsSessionErrorWithCallbackAndHandler(null /* handler */);
        testStartUpdateCredentialsSessionErrorWithCallbackAndHandler(
                new Handler(Looper.getMainLooper()));
    }

    private void testStartUpdateCredentialsSessionWithCallbackAndHandler(Handler handler)
            throws IOException, AuthenticatorException, OperationCanceledException {
        final Bundle options = new Bundle();
        final String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@"
                + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, getSessionBundle(null));
        options.putAll(OPTIONS_BUNDLE);

        final CountDownLatch latch = new CountDownLatch(1);

        AccountManagerCallback<Bundle> callback = new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> bundleFuture) {
                Bundle resultBundle = null;
                try {
                    resultBundle = bundleFuture.getResult();
                } catch (OperationCanceledException e) {
                    fail("should not throw an OperationCanceledException");
                } catch (IOException e) {
                    fail("should not throw an IOException");
                } catch (AuthenticatorException e) {
                    fail("should not throw an AuthenticatorException");
                }

                // Assert parameters has been passed correctly
                assertEquals(AUTH_TOKEN_TYPE, mockAuthenticator.getAuthTokenType());
                assertEquals(ACCOUNT, mockAuthenticator.mAccount);

                validateStartUpdateCredentialsSessionOptions(accountName, options);

                // Assert returned result
                // Assert that auth token was stripped.
                assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
                validateSessionBundleAndPasswordAndStatusTokenResult(resultBundle);

                latch.countDown();
            }
        };

        startUpdateCredentialsSession(
                am,
                ACCOUNT,
                AUTH_TOKEN_TYPE,
                options,
                mActivity,
                callback,
                handler);

        // Wait with timeout for the callback to do its work
        try {
            latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }
    }

    private void testStartUpdateCredentialsSessionWithCallbackAndHandlerWithIntervene(
            Handler handler)
                    throws IOException, AuthenticatorException, OperationCanceledException {
        final Bundle options = new Bundle();
        final String accountName = Fixtures.PREFIX_NAME_INTERVENE + "@"
                + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, getSessionBundle(null));
        options.putAll(OPTIONS_BUNDLE);

        final CountDownLatch latch = new CountDownLatch(1);

        AccountManagerCallback<Bundle> callback = new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> bundleFuture) {
                Bundle resultBundle = null;
                try {
                    resultBundle = bundleFuture.getResult();
                } catch (OperationCanceledException e) {
                    fail("should not throw an OperationCanceledException");
                } catch (IOException e) {
                    fail("should not throw an IOException");
                } catch (AuthenticatorException e) {
                    fail("should not throw an AuthenticatorException");
                }

                // Assert parameters has been passed correctly
                assertEquals(AUTH_TOKEN_TYPE, mockAuthenticator.getAuthTokenType());
                assertEquals(ACCOUNT, mockAuthenticator.mAccount);

                validateStartUpdateCredentialsSessionOptions(accountName, options);

                // Assert returned result
                assertNull(resultBundle.getParcelable(AccountManager.KEY_INTENT));
                // Assert that auth token was stripped.
                assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
                validateSessionBundleAndPasswordAndStatusTokenResult(resultBundle);

                latch.countDown();
            }
        };

        startUpdateCredentialsSession(
                am,
                ACCOUNT,
                AUTH_TOKEN_TYPE,
                options,
                mActivity,
                callback,
                handler);

        // Wait with timeout for the callback to do its work
        try {
            latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }
    }

    private void testStartUpdateCredentialsSessionWithCallbackAndHandlerWithReturnIntent(
            Handler handler)
                    throws IOException, AuthenticatorException, OperationCanceledException {
        final Bundle options = new Bundle();
        final String accountName = Fixtures.PREFIX_NAME_INTERVENE + "@"
                + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, getSessionBundle(null));
        options.putAll(OPTIONS_BUNDLE);

        final CountDownLatch latch = new CountDownLatch(1);

        AccountManagerCallback<Bundle> callback = new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> bundleFuture) {
                Bundle resultBundle = null;
                try {
                    resultBundle = bundleFuture.getResult();
                } catch (OperationCanceledException e) {
                    fail("should not throw an OperationCanceledException");
                } catch (IOException e) {
                    fail("should not throw an IOException");
                } catch (AuthenticatorException e) {
                    fail("should not throw an AuthenticatorException");
                }

                // Assert parameters has been passed correctly
                assertEquals(AUTH_TOKEN_TYPE, mockAuthenticator.getAuthTokenType());
                assertEquals(ACCOUNT, mockAuthenticator.mAccount);

                validateStartUpdateCredentialsSessionOptions(accountName, options);

                // Assert returned result
                Intent returnIntent = resultBundle.getParcelable(AccountManager.KEY_INTENT);
                // Assert KEY_INTENT is returned.
                assertNotNull(returnIntent);
                assertNotNull(returnIntent.getParcelableExtra(Fixtures.KEY_RESULT));
                // Assert that no other data is returned.
                assertNull(resultBundle.getString(AccountManager.KEY_ACCOUNT_STATUS_TOKEN));
                assertNull(resultBundle.getString(AccountManager.KEY_PASSWORD));
                assertNull(resultBundle.getString(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE));
                assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));

                latch.countDown();
            }
        };

        startUpdateCredentialsSession(
                am,
                ACCOUNT,
                AUTH_TOKEN_TYPE,
                options,
                null,
                callback,
                handler);

        // Wait with timeout for the callback to do its work
        try {
            latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }
    }

    private void testStartUpdateCredentialsSessionErrorWithCallbackAndHandler(Handler handler)
            throws IOException, OperationCanceledException {
        final Bundle options = new Bundle();
        final String accountName = Fixtures.PREFIX_NAME_ERROR + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, getSessionBundle(null));
        options.putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_INVALID_RESPONSE);
        options.putString(AccountManager.KEY_ERROR_MESSAGE, ERROR_MESSAGE);
        options.putAll(OPTIONS_BUNDLE);

        final CountDownLatch latch = new CountDownLatch(1);

        AccountManagerCallback<Bundle> callback = new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> bundleFuture) {
                try {
                    bundleFuture.getResult();
                    fail("should have thrown an AuthenticatorException");
                } catch (OperationCanceledException e) {
                    fail("should not throw an OperationCanceledException");
                } catch (IOException e) {
                    fail("should not throw an IOException");
                } catch (AuthenticatorException e) {
                    latch.countDown();
                }
            }
        };

        try {
            startUpdateCredentialsSession(
                    am,
                    ACCOUNT,
                    AUTH_TOKEN_TYPE,
                    options,
                    mActivity,
                    callback,
                    handler);
            // AuthenticatorException should be thrown when authenticator
            // returns AccountManager.ERROR_CODE_INVALID_RESPONSE.
            fail("should have thrown an AuthenticatorException");
        } catch (AuthenticatorException e1) {
        }

        // Wait with timeout for the callback to do its work
        try {
            latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }
    }

    private Bundle startUpdateCredentialsSession(AccountManager am, Account account,
            String authTokenType, Bundle options, Activity activity,
            AccountManagerCallback<Bundle> callback, Handler handler)
                    throws IOException, AuthenticatorException, OperationCanceledException {

        AccountManagerFuture<Bundle> futureBundle = am.startUpdateCredentialsSession(account,
                authTokenType, options, activity, callback, handler);

        Bundle resultBundle = futureBundle.getResult();
        assertTrue(futureBundle.isDone());
        assertNotNull(resultBundle);

        return resultBundle;
    }

    private void validateStartUpdateCredentialsSessionOptions(String accountName, Bundle options) {
        validateOptions(options, mockAuthenticator.mOptionsStartUpdateCredentialsSession);
        assertNotNull(mockAuthenticator.mOptionsStartUpdateCredentialsSession);
        assertEquals(accountName, mockAuthenticator.mOptionsStartUpdateCredentialsSession
                .getString(Fixtures.KEY_ACCOUNT_NAME));

        validateOptions(null, mockAuthenticator.mOptionsUpdateCredentials);
        validateOptions(null, mockAuthenticator.mOptionsConfirmCredentials);
        validateOptions(null, mockAuthenticator.mOptionsGetAuthToken);
        validateOptions(null, mockAuthenticator.mOptionsAddAccount);
        validateOptions(null, mockAuthenticator.mOptionsStartAddAccountSession);
    }

    /**
     * Tests a basic finishSession() with session bundle created by
     * startAddAccountSession(...). A bundle containing account name and account
     * type is expected.
     */
    public void testFinishSessionWithStartAddAccountSession()
            throws IOException, AuthenticatorException, OperationCanceledException {
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        Bundle sessionBundle = getSessionBundle(accountName);
        Bundle options = new Bundle();
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, sessionBundle);
        options.putAll(OPTIONS_BUNDLE);

        // First get an encrypted session bundle from startAddAccountSession(...)
        Bundle resultBundle = startAddAccountSession(
                am,
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                REQUIRED_FEATURES,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        // Assert returned result
        // Assert that auth token was stripped.
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
        validateSessionBundleAndPasswordAndStatusTokenResult(resultBundle);
        Bundle encryptedSessionBundle = resultBundle
                .getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);

        // Cleanup before calling finishSession(...) with the encrypted session bundle.
        mockAuthenticator.clearData();
        resultBundle = finishSession(
                am,
                encryptedSessionBundle,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        // Assert parameters has been passed correctly
        assertEquals(ACCOUNT_TYPE, mockAuthenticator.getAccountType());
        validateFinishSessionOptions(accountName, sessionBundle);

        // Assert returned result containing account name, type but not auth token type.
        validateAccountAndNoAuthTokenResult(resultBundle);
    }

    /**
     * Tests a basic finishSession() with session bundle created by
     * startUpdateCredentialsSession(...). A bundle containing account name and account
     * type is expected.
     */
    public void testFinishSessionWithStartUpdateCredentialsSession()
            throws IOException, AuthenticatorException, OperationCanceledException {
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        Bundle sessionBundle = getSessionBundle(accountName);
        Bundle options = new Bundle();
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, sessionBundle);
        options.putAll(OPTIONS_BUNDLE);

        // First get an encrypted session bundle from startUpdateCredentialsSession(...)
        Bundle resultBundle = startUpdateCredentialsSession(
                am,
                ACCOUNT,
                AUTH_TOKEN_TYPE,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        // Assert returned result
        // Assert that auth token was stripped.
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
        validateSessionBundleAndPasswordAndStatusTokenResult(resultBundle);
        Bundle encryptedSessionBundle = resultBundle
                .getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);

        // Cleanup before calling finishSession(...) with the encrypted session bundle.
        mockAuthenticator.clearData();
        resultBundle = finishSession(
                am,
                encryptedSessionBundle,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        // Assert parameters has been passed correctly
        assertEquals(ACCOUNT_TYPE, mockAuthenticator.getAccountType());
        validateFinishSessionOptions(accountName, sessionBundle);

        // Assert returned result containing account name, type but not auth token type.
        validateAccountAndNoAuthTokenResult(resultBundle);
    }

    /**
     * Tests finishSession() with null session bundle. IllegalArgumentException
     * is expected as session bundle cannot be null.
     */
    public void testFinishSessionWithNullSessionBundle()
            throws IOException, AuthenticatorException, OperationCanceledException {
        try {
            finishSession(
                    am,
                    null /* sessionBundle */,
                    null /* activity */,
                    null /* callback */,
                    null /* handler */);
            fail("Should have thrown IllegalArgumentException when sessionBundle is null");
        } catch (IllegalArgumentException e) {

        }
    }

    /**
     * Tests finishSession() with empty session bundle. IllegalArgumentException
     * is expected as session bundle would always contain something if it was
     * processed properly by AccountManagerService.
     */
    public void testFinishSessionWithEmptySessionBundle()
            throws IOException, AuthenticatorException, OperationCanceledException {

        try {
            finishSession(am,
                    new Bundle(),
                    null /* activity */,
                    null /* callback */,
                    null /* handler */);
            fail("Should have thrown IllegalArgumentException when sessionBundle is empty");
        } catch (IllegalArgumentException e) {

        }
    }

    /**
     * Tests finishSession() with sessionBundle not encrypted by the right key.
     * AuthenticatorException is expected if AccountManagerService failed to
     * decrypt the session bundle because of wrong key or crypto data was
     * tampered.
     */
    public void testFinishSessionWithDecryptionError()
            throws IOException, OperationCanceledException {
        byte[] mac = new byte[] {
                1, 1, 0, 0
        };
        byte[] cipher = new byte[] {
                1, 0, 0, 1, 1
        };
        Bundle sessionBundle = new Bundle();
        sessionBundle.putByteArray(KEY_MAC, mac);
        sessionBundle.putByteArray(KEY_CIPHER, cipher);

        try {
            finishSession(am,
                    sessionBundle,
                    null /* activity */,
                    null /* callback */,
                    null /* handler */);
            fail("Should have thrown AuthenticatorException when failed to decrypt sessionBundle");
        } catch (AuthenticatorException e) {

        }
    }

    /**
     * Tests finishSession() with sessionBundle invalid contents.
     * AuthenticatorException is expected if AccountManagerService failed to
     * decrypt the session bundle because of wrong key or crypto data was
     * tampered.
     */
    public void testFinishSessionWithInvalidEncryptedContent()
            throws IOException, OperationCanceledException {
        byte[] mac = new byte[] {};
        Bundle sessionBundle = new Bundle();
        sessionBundle.putByteArray(KEY_MAC, mac);

        try {
            finishSession(am,
                    sessionBundle,
                    null /* activity */,
                    null /* callback */,
                    null /* handler */);
            fail("Should have thrown AuthenticatorException when failed to decrypt sessionBundle");
        } catch (AuthenticatorException e) {

        }
    }

    /**
     * Tests a finishSession() when account type is not added to session bundle
     * by startAddAccount(...) of authenticator. A bundle containing account
     * name and account type should still be returned as AccountManagerSerivce
     * will always add account type to the session bundle before encrypting it.
     */
    public void testFinishSessionFromStartAddAccountWithoutAccountType()
            throws IOException, AuthenticatorException, OperationCanceledException {
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;

        // Create a session bundle without account type for MockAccountAuthenticator to return
        Bundle sessionBundle = getSessionBundle(accountName);
        sessionBundle.remove(AccountManager.KEY_ACCOUNT_TYPE);

        Bundle options = new Bundle();
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, sessionBundle);
        options.putAll(OPTIONS_BUNDLE);

        // First get an encrypted session bundle from startAddAccountSession(...)
        Bundle resultBundle = startAddAccountSession(
                am,
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                REQUIRED_FEATURES,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        // Assert returned result
        // Assert that auth token was stripped.
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
        validateSessionBundleAndPasswordAndStatusTokenResult(resultBundle);
        Bundle encryptedSessionBundle = resultBundle
                .getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);

        // Cleanup before calling finishSession(...) with the encrypted session bundle.
        mockAuthenticator.clearData();
        resultBundle = finishSession(
                am,
                encryptedSessionBundle,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        // Assert parameters has been passed correctly
        assertEquals(ACCOUNT_TYPE, mockAuthenticator.getAccountType());

        validateFinishSessionOptions(accountName, sessionBundle);

        // Assert returned result containing account name, type but not auth token type.
        validateAccountAndNoAuthTokenResult(resultBundle);
    }

    /**
     * Tests a finishSession() when account type is not added to session bundle
     * by startUpdateCredentialsSession(...) of authenticator. A bundle
     * containing account name and account type should still be returned as
     * AccountManagerSerivce will always add account type to the session bundle
     * before encrypting it.
     */
    public void testFinishSessionFromStartUpdateCredentialsSessionWithoutAccountType()
            throws IOException, AuthenticatorException, OperationCanceledException {
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;

        // Create a session bundle without account type for MockAccountAuthenticator to return
        Bundle sessionBundle = getSessionBundle(accountName);
        sessionBundle.remove(AccountManager.KEY_ACCOUNT_TYPE);

        Bundle options = new Bundle();
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, sessionBundle);
        options.putAll(OPTIONS_BUNDLE);

        // First get an encrypted session bundle from startAddAccountSession(...)
        Bundle resultBundle = startUpdateCredentialsSession(
                am,
                ACCOUNT,
                AUTH_TOKEN_TYPE,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        // Assert returned result
        // Assert that auth token was stripped.
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
        validateSessionBundleAndPasswordAndStatusTokenResult(resultBundle);
        Bundle encryptedSessionBundle = resultBundle
                .getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);

        // Cleanup before calling finishSession(...) with the encrypted session bundle.
        mockAuthenticator.clearData();
        resultBundle = finishSession(
                am,
                encryptedSessionBundle,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        // Assert parameters has been passed correctly
        assertEquals(ACCOUNT_TYPE, mockAuthenticator.getAccountType());

        validateFinishSessionOptions(accountName, sessionBundle);

        // Assert returned result containing account name, type but not auth token type.
        validateAccountAndNoAuthTokenResult(resultBundle);
    }

    /**
     * Tests a finishSession() when a different account type is added to session bundle
     * by startAddAccount(...) of authenticator. A bundle containing account
     * name and the correct account type should be returned as AccountManagerSerivce
     * will always overrides account type to the session bundle before encrypting it.
     */
    public void testFinishSessionFromStartAddAccountAccountTypeOverriden()
            throws IOException, AuthenticatorException, OperationCanceledException {
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;

        // Create a session bundle with a different account type for
        // MockAccountAuthenticator to return
        Bundle sessionBundle = getSessionBundle(accountName);
        sessionBundle.putString(AccountManager.KEY_ACCOUNT_TYPE, "randomAccountType");

        Bundle options = new Bundle();
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, sessionBundle);
        options.putAll(OPTIONS_BUNDLE);

        // First get an encrypted session bundle from startAddAccountSession(...)
        Bundle resultBundle = startAddAccountSession(
                am,
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                REQUIRED_FEATURES,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        // Assert returned result
        // Assert that auth token was stripped.
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
        validateSessionBundleAndPasswordAndStatusTokenResult(resultBundle);
        Bundle encryptedSessionBundle = resultBundle
                .getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);

        // Cleanup before calling finishSession(...) with the encrypted session bundle.
        mockAuthenticator.clearData();
        resultBundle = finishSession(
                am,
                encryptedSessionBundle,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        // Assert parameters has been passed correctly
        assertEquals(ACCOUNT_TYPE, mockAuthenticator.getAccountType());

        validateFinishSessionOptions(accountName, sessionBundle);

        // Assert returned result containing account name, correct type but not auth token type.
        validateAccountAndNoAuthTokenResult(resultBundle);
    }

    /**
     * Tests a finishSession() when a different account type is added to session bundle
     * by startUpdateCredentialsSession(...) of authenticator. A bundle
     * containing account name and the correct account type should be returned as
     * AccountManagerSerivce will always override account type to the session bundle
     * before encrypting it.
     */
    public void testFinishSessionFromStartUpdateCredentialsSessionAccountTypeOverriden()
            throws IOException, AuthenticatorException, OperationCanceledException {
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;

        // Create a session bundle with a different account type for
        // MockAccountAuthenticator to return
        Bundle sessionBundle = getSessionBundle(accountName);
        sessionBundle.putString(AccountManager.KEY_ACCOUNT_TYPE, "randomAccountType");

        Bundle options = new Bundle();
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, sessionBundle);
        options.putAll(OPTIONS_BUNDLE);

        // First get an encrypted session bundle from startAddAccountSession(...)
        Bundle resultBundle = startUpdateCredentialsSession(
                am,
                ACCOUNT,
                AUTH_TOKEN_TYPE,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        // Assert returned result
        // Assert that auth token was stripped.
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
        validateSessionBundleAndPasswordAndStatusTokenResult(resultBundle);
        Bundle encryptedSessionBundle = resultBundle
                .getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);

        // Cleanup before calling finishSession(...) with the encrypted session bundle.
        mockAuthenticator.clearData();
        resultBundle = finishSession(
                am,
                encryptedSessionBundle,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        // Assert parameters has been passed correctly
        assertEquals(ACCOUNT_TYPE, mockAuthenticator.getAccountType());

        validateFinishSessionOptions(accountName, sessionBundle);

        // Assert returned result containing account name, correct type but not auth token type.
        validateAccountAndNoAuthTokenResult(resultBundle);
    }

    /**
     * Tests finishSession with authenticator activity started. When additional
     * info is needed from user for finishing the session and an Activity was
     * provided by caller, the resolution intent will be started automatically.
     * A bundle containing account name and type will be returned.
     */
    public void testFinishSessionIntervene()
            throws IOException, AuthenticatorException, OperationCanceledException {
        String accountName = Fixtures.PREFIX_NAME_INTERVENE + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        Bundle sessionBundle = getSessionBundle(accountName);
        Bundle options = new Bundle();
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, sessionBundle);
        options.putAll(OPTIONS_BUNDLE);

        // First get an encrypted session bundle from startAddAccountSession(...)
        Bundle resultBundle = startAddAccountSession(
                am,
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                REQUIRED_FEATURES,
                options,
                mActivity,
                null /* callback */,
                null /* handler */);

        // Assert returned result
        assertNull(resultBundle.getParcelable(AccountManager.KEY_INTENT));
        // Assert that auth token was stripped.
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
        validateSessionBundleAndPasswordAndStatusTokenResult(resultBundle);
        Bundle encryptedSessionBundle = resultBundle
                .getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);

        // Cleanup before calling finishSession(...) with the encrypted session bundle.
        mockAuthenticator.clearData();
        resultBundle = finishSession(
                am,
                encryptedSessionBundle,
                mActivity,
                null /* callback */,
                null /* handler */);

        // Assert parameters has been passed correctly
        assertEquals(ACCOUNT_TYPE, mockAuthenticator.getAccountType());

        validateFinishSessionOptions(accountName, sessionBundle);

        // Assert returned result
        assertNull(resultBundle.getParcelable(AccountManager.KEY_INTENT));
        // Assert returned result containing account name, type but not auth token type.
        validateAccountAndNoAuthTokenResult(resultBundle);
    }

    /**
     * Tests finishSession with KEY_INTENT returned but not started
     * automatically. When additional info is needed from user for finishing the
     * session and no Activity was provided by caller, the resolution intent
     * will not be started automatically. A bundle containing KEY_INTENT will be
     * returned instead.
     */
    public void testFinishSessionWithReturnIntent()
            throws IOException, AuthenticatorException, OperationCanceledException {
        String accountName = Fixtures.PREFIX_NAME_INTERVENE + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        Bundle sessionBundle = getSessionBundle(accountName);
        Bundle options = new Bundle();
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, sessionBundle);
        options.putAll(OPTIONS_BUNDLE);

        // First get an encrypted session bundle from startAddAccountSession(...)
        Bundle resultBundle = startAddAccountSession(
                am,
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                REQUIRED_FEATURES,
                options,
                mActivity,
                null /* callback */,
                null /* handler */);

        // Assert returned result
        assertNull(resultBundle.getParcelable(AccountManager.KEY_INTENT));
        // Assert that auth token was stripped.
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
        validateSessionBundleAndPasswordAndStatusTokenResult(resultBundle);
        Bundle encryptedSessionBundle = resultBundle
                .getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);

        // Cleanup before calling finishSession(...) with the encrypted session bundle.
        mockAuthenticator.clearData();
        resultBundle = finishSession(am, encryptedSessionBundle, null /* activity */,
                null /* callback */, null /* handler */);

        // Assert parameters has been passed correctly
        assertEquals(ACCOUNT_TYPE, mockAuthenticator.getAccountType());

        validateFinishSessionOptions(accountName, sessionBundle);

        // Assert returned result
        Intent returnIntent = resultBundle.getParcelable(AccountManager.KEY_INTENT);
        assertNotNull(returnIntent);
        assertNotNull(returnIntent.getParcelableExtra(Fixtures.KEY_RESULT));

        assertNull(resultBundle.get(AccountManager.KEY_ACCOUNT_NAME));
        assertNull(resultBundle.get(AccountManager.KEY_ACCOUNT_TYPE));
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
    }

    /**
     * Tests finishSession error case. AuthenticatorException is expected when
     * AccountManager.ERROR_CODE_INVALID_RESPONSE is returned by authenticator.
     */
    public void testFinishSessionError()
            throws IOException, AuthenticatorException, OperationCanceledException {
        Bundle sessionBundle = new Bundle();
        String accountNameForFinish = Fixtures.PREFIX_NAME_ERROR + "@"
                + Fixtures.SUFFIX_NAME_FIXTURE;
        sessionBundle.putString(Fixtures.KEY_ACCOUNT_NAME, accountNameForFinish);
        sessionBundle.putInt(AccountManager.KEY_ERROR_CODE,
                AccountManager.ERROR_CODE_INVALID_RESPONSE);
        sessionBundle.putString(AccountManager.KEY_ERROR_MESSAGE, ERROR_MESSAGE);

        Bundle options = new Bundle();
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, sessionBundle);
        options.putAll(OPTIONS_BUNDLE);

        // First get an encrypted session bundle from startAddAccountSession(...)
        Bundle resultBundle = startAddAccountSession(
                am,
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                REQUIRED_FEATURES,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        // Assert returned result
        // Assert that auth token was stripped.
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
        validateSessionBundleAndPasswordAndStatusTokenResult(resultBundle);
        Bundle encryptedSessionBundle = resultBundle
                .getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);

        // Cleanup before calling finishSession(...) with the encrypted session bundle.
        mockAuthenticator.clearData();

        try {
            finishSession(
                    am,
                    encryptedSessionBundle,
                    null /* activity */,
                    null /* callback */,
                    null /* handler */);
            fail("finishSession should throw AuthenticatorException in error case.");
        } catch (AuthenticatorException e) {
        }
    }

    /**
     * Tests finishSession() with callback and handler. A bundle containing
     * account name and type should be returned via the callback regardless of
     * whether a handler is provided.
     */
    public void testFinishSessionWithCallbackAndHandler()
            throws IOException, AuthenticatorException, OperationCanceledException {

        testFinishSessionWithCallbackAndHandler(null /* handler */);
        testFinishSessionWithCallbackAndHandler(new Handler(Looper.getMainLooper()));
    }

    /**
     * Tests finishSession() with callback and handler and activity started.
     * When additional info is needed from user for finishing the session and an
     * Activity was provided by caller, the resolution intent will be started
     * automatically. A bundle containing account name and type will be returned
     * via the callback regardless of if handler is provided or now.
     */
    public void testFinishSessionWithCallbackAndHandlerWithIntervene()
            throws IOException, AuthenticatorException, OperationCanceledException {

        testFinishSessionWithCallbackAndHandlerWithIntervene(null /* handler */);
        testFinishSessionWithCallbackAndHandlerWithIntervene(
                new Handler(Looper.getMainLooper()));
    }

    /**
     * Tests finishSession() with callback and handler with KEY_INTENT
     * returned. When additional info is needed from user for finishing the
     * session and no Activity was provided by caller, the resolution intent
     * will not be started automatically. A bundle containing KEY_INTENT will be
     * returned instead via callback regardless of if handler is provided or not.
     */
    public void testFinishSessionWithCallbackAndHandlerWithReturnIntent()
            throws IOException, AuthenticatorException, OperationCanceledException {

        testFinishSessionWithCallbackAndHandlerWithReturnIntent(null /* handler */);
        testFinishSessionWithCallbackAndHandlerWithReturnIntent(
                new Handler(Looper.getMainLooper()));
    }

    /**
     * Tests finishSession() error case with callback and handler.
     * AuthenticatorException is expected when
     * AccountManager.ERROR_CODE_INVALID_RESPONSE is returned by authenticator.
     */
    public void testFinishSessionErrorWithCallbackAndHandler()
            throws IOException, OperationCanceledException, AuthenticatorException {

        testFinishSessionErrorWithCallbackAndHandler(null /* handler */);
        testFinishSessionErrorWithCallbackAndHandler(new Handler(Looper.getMainLooper()));
    }

    private void testFinishSessionWithCallbackAndHandler(Handler handler)
            throws IOException, AuthenticatorException, OperationCanceledException {
        final String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@"
                + Fixtures.SUFFIX_NAME_FIXTURE;
        final Bundle sessionBundle = getSessionBundle(accountName);
        Bundle options = new Bundle();
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, sessionBundle);
        options.putAll(OPTIONS_BUNDLE);

        // First get an encrypted session bundle from startAddAccountSession(...)
        Bundle resultBundle = startAddAccountSession(
                am,
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                REQUIRED_FEATURES,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        // Assert returned result
        // Assert that auth token was stripped.
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
        validateSessionBundleAndPasswordAndStatusTokenResult(resultBundle);
        Bundle encryptedSessionBundle = resultBundle
                .getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);

        final CountDownLatch latch = new CountDownLatch(1);

        AccountManagerCallback<Bundle> callback = new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> bundleFuture) {
                Bundle resultBundle = null;
                try {
                    resultBundle = bundleFuture.getResult();
                } catch (OperationCanceledException e) {
                    fail("should not throw an OperationCanceledException");
                } catch (IOException e) {
                    fail("should not throw an IOException");
                } catch (AuthenticatorException e) {
                    fail("should not throw an AuthenticatorException");
                }

                // Assert parameters has been passed correctly
                assertEquals(ACCOUNT_TYPE, mockAuthenticator.getAccountType());

                validateFinishSessionOptions(accountName, sessionBundle);

                // Assert returned result containing account name, type but not auth token type.
                validateAccountAndNoAuthTokenResult(resultBundle);

                latch.countDown();
            }
        };

        // Cleanup before calling finishSession(...) with the encrypted session bundle.
        mockAuthenticator.clearData();
        finishSession(am, encryptedSessionBundle, mActivity, callback, handler);

        // Wait with timeout for the callback to do its work
        try {
            latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }
    }

    private void testFinishSessionWithCallbackAndHandlerWithIntervene(Handler handler)
            throws IOException, AuthenticatorException, OperationCanceledException {
        final String accountName = Fixtures.PREFIX_NAME_INTERVENE + "@"
                + Fixtures.SUFFIX_NAME_FIXTURE;
        final Bundle sessionBundle = getSessionBundle(accountName);
        Bundle options = new Bundle();
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, sessionBundle);
        options.putAll(OPTIONS_BUNDLE);

        // First get an encrypted session bundle from startAddAccountSession(...)
        Bundle resultBundle = startAddAccountSession(
                am,
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                REQUIRED_FEATURES,
                options,
                mActivity,
                null /* callback */,
                null /* handler */);

        // Assert returned result
        assertNull(resultBundle.getParcelable(AccountManager.KEY_INTENT));
        // Assert that auth token was stripped.
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
        validateSessionBundleAndPasswordAndStatusTokenResult(resultBundle);
        Bundle encryptedSessionBundle = resultBundle
                .getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);

        final CountDownLatch latch = new CountDownLatch(1);

        AccountManagerCallback<Bundle> callback = new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> bundleFuture) {
                Bundle resultBundle = null;
                try {
                    resultBundle = bundleFuture.getResult();
                } catch (OperationCanceledException e) {
                    fail("should not throw an OperationCanceledException");
                } catch (IOException e) {
                    fail("should not throw an IOException");
                } catch (AuthenticatorException e) {
                    fail("should not throw an AuthenticatorException");
                }

                // Assert parameters has been passed correctly
                assertEquals(ACCOUNT_TYPE, mockAuthenticator.getAccountType());

                validateFinishSessionOptions(accountName, sessionBundle);

                // Assert returned result
                assertNull(resultBundle.getParcelable(AccountManager.KEY_INTENT));
                // Assert returned result containing account name, type but not auth token type.
                validateAccountAndNoAuthTokenResult(resultBundle);

                latch.countDown();
            }
        };

        // Cleanup before calling finishSession(...) with the encrypted session bundle.
        mockAuthenticator.clearData();
        finishSession(am, encryptedSessionBundle, mActivity, callback, handler);

        // Wait with timeout for the callback to do its work
        try {
            latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }
    }

    private void testFinishSessionWithCallbackAndHandlerWithReturnIntent(Handler handler)
            throws IOException, AuthenticatorException, OperationCanceledException {
        final String accountName = Fixtures.PREFIX_NAME_INTERVENE + "@"
                + Fixtures.SUFFIX_NAME_FIXTURE;
        final Bundle sessionBundle = getSessionBundle(accountName);
        Bundle options = new Bundle();
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, sessionBundle);
        options.putAll(OPTIONS_BUNDLE);

        // First get an encrypted session bundle from startAddAccountSession(...)
        Bundle resultBundle = startAddAccountSession(
                am,
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                REQUIRED_FEATURES,
                options,
                mActivity,
                null /* callback */,
                null /* handler */);

        // Assert returned result
        assertNull(resultBundle.getParcelable(AccountManager.KEY_INTENT));
        // Assert that auth token was stripped.
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
        validateSessionBundleAndPasswordAndStatusTokenResult(resultBundle);
        Bundle encryptedSessionBundle = resultBundle
                .getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);

        final CountDownLatch latch = new CountDownLatch(1);

        AccountManagerCallback<Bundle> callback = new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> bundleFuture) {
                Bundle resultBundle = null;
                try {
                    resultBundle = bundleFuture.getResult();
                } catch (OperationCanceledException e) {
                    fail("should not throw an OperationCanceledException");
                } catch (IOException e) {
                    fail("should not throw an IOException");
                } catch (AuthenticatorException e) {
                    fail("should not throw an AuthenticatorException");
                }

                // Assert parameters has been passed correctly
                assertEquals(ACCOUNT_TYPE, mockAuthenticator.getAccountType());

                validateFinishSessionOptions(accountName, sessionBundle);

                // Assert returned result
                Intent returnIntent = resultBundle.getParcelable(AccountManager.KEY_INTENT);
                assertNotNull(returnIntent);
                assertNotNull(returnIntent.getParcelableExtra(Fixtures.KEY_RESULT));

                assertNull(resultBundle.get(AccountManager.KEY_ACCOUNT_NAME));
                assertNull(resultBundle.get(AccountManager.KEY_ACCOUNT_TYPE));
                assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));

                latch.countDown();
            }
        };

        // Cleanup before calling finishSession(...) with the encrypted session bundle.
        mockAuthenticator.clearData();
        finishSession(am, encryptedSessionBundle, null, callback, handler);

        // Wait with timeout for the callback to do its work
        try {
            latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }
    }

    private void testFinishSessionErrorWithCallbackAndHandler(Handler handler)
            throws IOException, OperationCanceledException, AuthenticatorException {
        Bundle sessionBundle = new Bundle();
        String accountNameForFinish = Fixtures.PREFIX_NAME_ERROR + "@"
                + Fixtures.SUFFIX_NAME_FIXTURE;
        sessionBundle.putString(Fixtures.KEY_ACCOUNT_NAME, accountNameForFinish);
        sessionBundle.putInt(AccountManager.KEY_ERROR_CODE,
                AccountManager.ERROR_CODE_INVALID_RESPONSE);
        sessionBundle.putString(AccountManager.KEY_ERROR_MESSAGE, ERROR_MESSAGE);

        Bundle options = new Bundle();
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, sessionBundle);
        options.putAll(OPTIONS_BUNDLE);

        // First get an encrypted session bundle from startAddAccountSession(...)
        Bundle resultBundle = startAddAccountSession(
                am,
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                REQUIRED_FEATURES,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        // Assert returned result
        // Assert that auth token was stripped.
        assertNull(resultBundle.get(AccountManager.KEY_AUTHTOKEN));
        validateSessionBundleAndPasswordAndStatusTokenResult(resultBundle);
        Bundle encryptedSessionBundle = resultBundle
                .getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);

        final CountDownLatch latch = new CountDownLatch(1);

        AccountManagerCallback<Bundle> callback = new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> bundleFuture) {
                try {
                    bundleFuture.getResult();
                    fail("should have thrown an AuthenticatorException");
                } catch (OperationCanceledException e) {
                    fail("should not throw an OperationCanceledException");
                } catch (IOException e) {
                    fail("should not throw an IOException");
                } catch (AuthenticatorException e) {
                    latch.countDown();
                }
            }
        };

        // Cleanup before calling finishSession(...) with the encrypted session bundle.
        mockAuthenticator.clearData();

        try {
            finishSession(am, encryptedSessionBundle, mActivity, callback, handler);
            fail("should have thrown an AuthenticatorException");
        } catch (AuthenticatorException e1) {
        }

        // Wait with timeout for the callback to do its work
        try {
            latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }
    }

    private Bundle finishSession(AccountManager am, Bundle sessionBundle, Activity activity,
            AccountManagerCallback<Bundle> callback, Handler handler)
                    throws IOException, AuthenticatorException, OperationCanceledException {

        AccountManagerFuture<Bundle> futureBundle = am.finishSession(
                sessionBundle,
                activity,
                callback,
                handler);

        Bundle resultBundle = futureBundle.getResult();
        assertTrue(futureBundle.isDone());
        assertNotNull(resultBundle);

        return resultBundle;
    }

    private void validateFinishSessionOptions(String accountName, Bundle options) {
        validateOptions(options, mockAuthenticator.mOptionsFinishSession);
        assertNotNull(mockAuthenticator.mOptionsFinishSession);
        assertEquals(ACCOUNT_TYPE, mockAuthenticator.mOptionsFinishSession
                .getString(AccountManager.KEY_ACCOUNT_TYPE));
        assertEquals(accountName,
                mockAuthenticator.mOptionsFinishSession.getString(Fixtures.KEY_ACCOUNT_NAME));

        validateSystemOptions(mockAuthenticator.mOptionsFinishSession);
        validateOptions(null, mockAuthenticator.mOptionsUpdateCredentials);
        validateOptions(null, mockAuthenticator.mOptionsConfirmCredentials);
        validateOptions(null, mockAuthenticator.mOptionsGetAuthToken);
        validateOptions(null, mockAuthenticator.mOptionsAddAccount);
        validateOptions(null, mockAuthenticator.mOptionsStartAddAccountSession);
        validateOptions(null, mockAuthenticator.mOptionsStartUpdateCredentialsSession);
    }
}
