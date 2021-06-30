package com.programtown.example;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.android.billingclient.api.BillingClient.SkuType.SUBS;

public class MainActivity extends AppCompatActivity implements PurchasesUpdatedListener {

    public static final String PREF_FILE= "MyPref";
    //note add unique product ids
    //use same id for preference key
    private static ArrayList<String> subcribeItemIDs = new ArrayList<String>() {{
        add("s1");
        add("s2");
        add("s3");
    }};
    private static ArrayList<String> subscribeItemDisplay = new ArrayList<String>();
    ArrayAdapter<String> arrayAdapter;
    ListView listView;

    private BillingClient billingClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView=(ListView) findViewById(R.id.listview);

        // Establish connection to billing client
        //check purchase status from google play store cache on every app start
        billingClient = BillingClient.newBuilder(this)
                .enablePendingPurchases().setListener(this).build();
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {

                if(billingResult.getResponseCode()==BillingClient.BillingResponseCode.OK){
                    Purchase.PurchasesResult queryPurchase = billingClient.queryPurchases(SUBS);
                    List<Purchase> queryPurchases = queryPurchase.getPurchasesList();
                    if(queryPurchases!=null && queryPurchases.size()>0){
                        handlePurchases(queryPurchases);
                    }

                    //check which items are in purchase list and which are not in purchase list
                    //if items that are found add them to purchaseFound
                    //check status of found items and save values to preference
                    //item which are not found simply save false values to their preference
                    //indexOf return index of item in purchase list from 0-2 (because we have 3 items) else returns -1 if not found
                    ArrayList<Integer> purchaseFound =new ArrayList<Integer> ();
                    if(queryPurchases!=null && queryPurchases.size()>0){
                        //check item in purchase list
                        for(Purchase p:queryPurchases){
                            int index=subcribeItemIDs.indexOf(p.getSku());
                            //if purchase found
                            if(index>-1)
                            {
                                purchaseFound.add(index);
                                if(p.getPurchaseState() == Purchase.PurchaseState.PURCHASED)
                                {
                                    saveSubscribeItemValueToPref(subcribeItemIDs.get(index),true);
                                }
                                else{
                                    saveSubscribeItemValueToPref(subcribeItemIDs.get(index),false);
                                }
                            }
                        }
                        //items that are not found in purchase list mark false
                        //indexOf returns -1 when item is not in foundlist
                        for(int i=0;i < subcribeItemIDs.size(); i++){
                            if(purchaseFound.indexOf(i)==-1){
                                saveSubscribeItemValueToPref(subcribeItemIDs.get(i),false);
                            }
                        }
                    }
                    //if purchase list is empty that means no item is not purchased/Subscribed
                    //Or purchase is refunded or canceled
                    //so mark them all false
                    else{
                        for( String purchaseItem: subcribeItemIDs ){
                            saveSubscribeItemValueToPref(purchaseItem,false);
                        }
                    }

                }

            }

            @Override
            public void onBillingServiceDisconnected() {
            }
        });


        arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, subscribeItemDisplay);
        listView.setAdapter(arrayAdapter);
        notifyList();

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {

                if(getSubscribeItemValueFromPref(subcribeItemIDs.get(position))){
                    Toast.makeText(getApplicationContext(),subcribeItemIDs.get(position)+" is Already Subscribed",Toast.LENGTH_SHORT).show();
                    //selected item is already purchased/subscribed
                    return;
                }
                //initiate purchase on selected product/subscribe item click
                //check if service is already connected
                if (billingClient.isReady()) {
                    initiatePurchase(subcribeItemIDs.get(position));
                }
                //else reconnect service
                else{
                    billingClient = BillingClient.newBuilder(MainActivity.this).enablePendingPurchases().setListener(MainActivity.this).build();
                    billingClient.startConnection(new BillingClientStateListener() {
                        @Override
                        public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                                initiatePurchase(subcribeItemIDs.get(position));
                            } else {
                                Toast.makeText(getApplicationContext(),"Error "+billingResult.getDebugMessage(),Toast.LENGTH_SHORT).show();
                            }
                        }
                        @Override
                        public void onBillingServiceDisconnected() {
                        }
                    });
                }
            }
        });
    }

    private void notifyList(){
        subscribeItemDisplay.clear();
        for(String p:subcribeItemIDs){
            subscribeItemDisplay.add("Subscribe Status of "+p+" = "+getSubscribeItemValueFromPref(p));
        }
        arrayAdapter.notifyDataSetChanged();
    }

    private SharedPreferences getPreferenceObject() {
        return getApplicationContext().getSharedPreferences(PREF_FILE, 0);
    }
    private SharedPreferences.Editor getPreferenceEditObject() {
        SharedPreferences pref = getApplicationContext().getSharedPreferences(PREF_FILE, 0);
        return pref.edit();
    }
    private boolean getSubscribeItemValueFromPref(String PURCHASE_KEY){
        return getPreferenceObject().getBoolean(PURCHASE_KEY,false);
    }
    private void saveSubscribeItemValueToPref(String PURCHASE_KEY,boolean value){
        getPreferenceEditObject().putBoolean(PURCHASE_KEY,value).commit();
    }


    private void initiatePurchase(final String PRODUCT_ID) {
        List<String> skuList = new ArrayList<>();
        skuList.add(PRODUCT_ID);
        SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
        params.setSkusList(skuList).setType(SUBS);

        BillingResult billingResult = billingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS);

        if(billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            billingClient.querySkuDetailsAsync(params.build(),
                    new SkuDetailsResponseListener() {
                        @Override
                        public void onSkuDetailsResponse(@NonNull BillingResult billingResult,
                                                         List<SkuDetails> skuDetailsList) {
                            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                                if (skuDetailsList != null && skuDetailsList.size() > 0) {
                                    BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                                            .setSkuDetails(skuDetailsList.get(0))
                                            .build();
                                    billingClient.launchBillingFlow(MainActivity.this, flowParams);
                                } else {
                                    //try to add item/product id "s1" "s2" "s3" inside subscription in google play console
                                    Toast.makeText(getApplicationContext(), "Subscribe Item " + PRODUCT_ID + " not Found", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(getApplicationContext(),
                                        " Error " + billingResult.getDebugMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        }
        else{
            Toast.makeText(getApplicationContext(),
                    "Sorry Subscription not Supported. Please Update Play Store", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, @Nullable List<Purchase> purchases) {
        //if item newly purchased
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases);
        }
        //if item already purchased then check and reflect changes
        else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            Purchase.PurchasesResult queryAlreadyPurchasesResult = billingClient.queryPurchases(SUBS);
            List<Purchase> alreadyPurchases = queryAlreadyPurchasesResult.getPurchasesList();
            if(alreadyPurchases!=null){
                handlePurchases(alreadyPurchases);
            }
        }
        //if purchase cancelled
        else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(getApplicationContext(),"Purchase Canceled",Toast.LENGTH_SHORT).show();
        }
        // Handle any other error msgs
        else {
            Toast.makeText(getApplicationContext(),"Error "+billingResult.getDebugMessage(),Toast.LENGTH_SHORT).show();
        }
    }
    void handlePurchases(List<Purchase>  purchases) {
        for(Purchase purchase:purchases) {

            final int index=subcribeItemIDs.indexOf(purchase.getSku());
            //purchase found
            if(index>-1) {

                //if item is purchased
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED)
                {
                    if (!verifyValidSignature(purchase.getOriginalJson(), purchase.getSignature())) {
                        // Invalid purchase
                        // show error to user
                        Toast.makeText(getApplicationContext(), "Error : Invalid Purchase", Toast.LENGTH_SHORT).show();
                        continue;//skip current iteration only because other items in purchase list must be checked if present
                    }
                    // else purchase is valid
                    //if item is purchased/subscribed and not Acknowledged
                    if (!purchase.isAcknowledged()) {
                        AcknowledgePurchaseParams acknowledgePurchaseParams =
                                AcknowledgePurchaseParams.newBuilder()
                                        .setPurchaseToken(purchase.getPurchaseToken())
                                        .build();

                        billingClient.acknowledgePurchase(acknowledgePurchaseParams,
                                new AcknowledgePurchaseResponseListener() {
                                    @Override
                                    public void onAcknowledgePurchaseResponse(BillingResult billingResult) {
                                        if(billingResult.getResponseCode()==BillingClient.BillingResponseCode.OK){
                                            //if purchase is acknowledged
                                            //then saved value in preference
                                            saveSubscribeItemValueToPref(subcribeItemIDs.get(index),true);
                                            Toast.makeText(getApplicationContext(), subcribeItemIDs.get(index)+" Item Subscribed", Toast.LENGTH_SHORT).show();
                                            notifyList();
                                        }
                                    }
                                });

                    }
                    //else item is purchased and also acknowledged
                    else {
                        // Grant entitlement to the user on item purchase
                        if(!getSubscribeItemValueFromPref(subcribeItemIDs.get(index))){
                            saveSubscribeItemValueToPref(subcribeItemIDs.get(index),true);
                            Toast.makeText(getApplicationContext(), subcribeItemIDs.get(index)+" Item Subscribed.", Toast.LENGTH_SHORT).show();
                            notifyList();
                        }
                    }
                }
                //if purchase is pending
                else if(  purchase.getPurchaseState() == Purchase.PurchaseState.PENDING)
                {
                    Toast.makeText(getApplicationContext(),
                            subcribeItemIDs.get(index)+" Purchase is Pending. Please complete Transaction", Toast.LENGTH_SHORT).show();
                }
                //if purchase is refunded or unknown
                else if( purchase.getPurchaseState() == Purchase.PurchaseState.UNSPECIFIED_STATE)
                {
                    //mark purchase false in case of UNSPECIFIED_STATE
                    saveSubscribeItemValueToPref(subcribeItemIDs.get(index),false);
                    Toast.makeText(getApplicationContext(), subcribeItemIDs.get(index)+" Purchase Status Unknown", Toast.LENGTH_SHORT).show();
                    notifyList();
                }
            }

        }

    }


    /**
     * Verifies that the purchase was signed correctly for this developer's public key.
     * <p>Note: It's strongly recommended to perform such check on your backend since hackers can
     * replace this method with "constant true" if they decompile/rebuild your app.
     * </p>
     */
    private boolean verifyValidSignature(String signedData, String signature) {
        try {
            //for old playconsole
            // To get key go to Developer Console > Select your app > Development Tools > Services & APIs.
            //for new play console
            //To get key go to Developer Console > Select your app > Monetize > Monetization setup

            String base64Key = "Add your key here";
            return Security.verifyPurchase(base64Key, signedData, signature);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(billingClient!=null){
            billingClient.endConnection();
        }
    }

}



