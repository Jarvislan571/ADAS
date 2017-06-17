/*
 * Copyright (c) 2017 Ahmed-Abdelmeged
 *
 * github: https://github.com/Ahmed-Abdelmeged
 * email: ahmed.abdelmeged.vm@gamil.com
 * Facebook: https://www.facebook.com/ven.rto
 * Twitter: https://twitter.com/A_K_Abd_Elmeged
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.mego.adas.auth;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.alimuzaffar.lib.pin.PinEntryEditText;
import com.example.mego.adas.MainActivity;
import com.example.mego.adas.R;
import com.example.mego.adas.utils.Constant;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.concurrent.TimeUnit;

/**
 * Activity used for verify user phone number
 */
public class VerifyPhoneNumberActivity extends AppCompatActivity {

    /**
     * Tag for the logs
     */
    private static final String LOG_TAG = VerifyPhoneNumberActivity.class.getSimpleName();

    /**
     * UI Element
     */
    private TextView resendTextView;
    private Button continueVerfiyingButton;
    private PinEntryEditText pinCodeEditText;

    /**
     * Firebase objects
     * to specific part of the database
     */
    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference isPhoneAuthDatabaseReference, phoneNumberDatabaseReference;
    private ValueEventListener phoneNumberValueEventListener;


    /**
     * Firebase Authentication
     */
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    private String uid = null;

    /**
     * Firebase phone verification
     */
    private static final String KEY_VERIFY_IN_PROGRESS = "key_verify_in_progress";
    private boolean mVerificationInProgress = false;
    private String mVerificationId;
    private PhoneAuthProvider.ForceResendingToken mResendToken;
    private PhoneAuthProvider.OnVerificationStateChangedCallbacks mVerificationCallbacks;
    private String userPhoneNumber = null;

    /**
     * Flag
     */
    private static final int INVALID_CODE_FLAG = 34;
    private static final int INVALID_LINKING = 35;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.AppTheme);
        setContentView(R.layout.activity_verify_phone_number);

        initializeScreen();

        // Restore instance state
        if (savedInstanceState != null) {
            onRestoreInstanceState(savedInstanceState);
        }

        //set up the firebase
        mFirebaseDatabase = FirebaseDatabase.getInstance();

        //initialize the Firebase auth object
        mFirebaseAuth = FirebaseAuth.getInstance();

        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                if (firebaseUser == null) {
                    Intent mainIntent = new Intent(VerifyPhoneNumberActivity.this, NotAuthEntryActivity.class);
                    //clear the application stack (clear all  former the activities)
                    mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(mainIntent);
                    finish();
                } else {
                    uid = firebaseUser.getUid();
                    if (AuthenticationUtilities.isAvailableInternetConnection(VerifyPhoneNumberActivity.this)) {
                        if (uid != null) {
                            isPhoneAuthDatabaseReference = mFirebaseDatabase.getReference().child(Constant.FIREBASE_USERS)
                                    .child(uid).child(Constant.FIREBASE_IS_VERIFIED_PHONE);

                            phoneNumberDatabaseReference = mFirebaseDatabase.getReference().child(Constant.FIREBASE_USERS)
                                    .child(uid).child(Constant.FIREBASE_USER_PHONE);

                            verificationStatesCallbacks();
                            getUserPhoneNumber();
                            startVerification();

                            resendTextView.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    if (userPhoneNumber != null) {
                                        resendVerificationCode(userPhoneNumber, mResendToken);
                                    }
                                }
                            });
                        }
                    } else {
                        Toast.makeText(VerifyPhoneNumberActivity.this, R.string.error_message_failed_sign_in_no_network,
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
    }

    /**
     * Method to track the verification states
     */
    private void verificationStatesCallbacks() {
        mVerificationCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            @Override
            public void onVerificationCompleted(PhoneAuthCredential phoneAuthCredential) {
                // the verification complete and set the is phone auth to true
                mVerificationInProgress = false;
                Log.e(LOG_TAG, "success");
                startApp();
            }

            @Override
            public void onVerificationFailed(FirebaseException e) {
                mVerificationInProgress = false;
                Log.e(LOG_TAG, "failed");
                if (e instanceof FirebaseAuthInvalidCredentialsException) {
                    pinCodeEditText.setText(null);
                    Toast.makeText(VerifyPhoneNumberActivity.this,
                            getString(R.string.invalid_phone_number), Toast.LENGTH_LONG).show();
                } else if (e instanceof FirebaseTooManyRequestsException) {
                    Toast.makeText(VerifyPhoneNumberActivity.this,
                            getString(R.string.unexpected_error_call_the_support), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCodeSent(String verificationId, PhoneAuthProvider.ForceResendingToken forceResendingToken) {
                Log.e(LOG_TAG, "code send");
                // Save verification ID and resending token
                mVerificationId = verificationId;
                mResendToken = forceResendingToken;

            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mVerificationInProgress && userPhoneNumber != null) {
            startPhoneNumberVerification(userPhoneNumber);
        }
    }

    /**
     * Method to request that Firebase verify the user's phone number
     */
    private void startPhoneNumberVerification(String phoneNumber) {
        Log.e(LOG_TAG, "start");
        PhoneAuthProvider.getInstance().verifyPhoneNumber(
                phoneNumber,        // Phone number to verify
                60,                 // Timeout duration
                TimeUnit.SECONDS,   // Unit of timeout
                this,               // Activity (for callback binding)
                mVerificationCallbacks);        // OnVerificationStateChangedCallbacks
        mVerificationInProgress = true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_VERIFY_IN_PROGRESS, mVerificationInProgress);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mVerificationInProgress = savedInstanceState.getBoolean(KEY_VERIFY_IN_PROGRESS);
    }

    /**
     * Method to resend the code
     */
    private void resendVerificationCode(String phoneNumber,
                                        PhoneAuthProvider.ForceResendingToken token) {
        Log.e(LOG_TAG, "resend");
        PhoneAuthProvider.getInstance().verifyPhoneNumber(
                phoneNumber,        // Phone number to verify
                60,                 // Timeout duration
                TimeUnit.SECONDS,   // Unit of timeout
                this,               // Activity (for callback binding)
                mVerificationCallbacks,         // OnVerificationStateChangedCallbacks
                token);             // ForceResendingToken from callbacks
    }

    /**
     * Method to get the user phone number
     */
    private void getUserPhoneNumber() {
        phoneNumberValueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    userPhoneNumber = dataSnapshot.getValue(String.class);
                    Log.e(LOG_TAG, userPhoneNumber);
                    if (userPhoneNumber != null) {
                        Log.e(LOG_TAG, "get number");
                        startPhoneNumberVerification(userPhoneNumber);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        };
        phoneNumberDatabaseReference.addListenerForSingleValueEvent(phoneNumberValueEventListener);
    }

    /**
     * Method to verify phone number with code
     */
    private void verifyPhoneNumberWithCode(String verificationId, String code) {
        if (code != null && verificationId != null) {
            PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
            linkEmailToPhoneNumber(credential);
        }
    }

    /**
     * Method to start verifying
     */
    private void startVerification() {
        continueVerfiyingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String code = pinCodeEditText.getText().toString();
                verifyPhoneNumberWithCode(mVerificationId, code);
            }
        });
    }

    /**
     * Method call after the auth done and start the app
     */
    private void startApp() {
        isPhoneAuthDatabaseReference.setValue(true);
        //start the main activity
        Intent mainIntent = new Intent(VerifyPhoneNumberActivity.this, MainActivity.class);
        //clear the application stack (clear all  former the activities)
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(mainIntent);
        finish();
    }

    /**
     * Method to link the phone number to email
     */
    private void linkEmailToPhoneNumber(final PhoneAuthCredential credential) {
        mFirebaseAuth.getCurrentUser().linkWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.e(LOG_TAG, "linking");
                            startApp();
                        }
                    }
                }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                if (e instanceof FirebaseAuthInvalidCredentialsException) {
                    showErrorDialog("Invalid code.", INVALID_CODE_FLAG);
                    pinCodeEditText.setText(null);
                } else {
                    showErrorDialog(e.getLocalizedMessage(), INVALID_LINKING);
                }
            }
        });
    }

    /**
     * Method to sign in with
     */
    /*private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mFirebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.e(LOG_TAG, "sign in");
                            startApp();
                        }
                    }
                }).addOnFailureListener(this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                if (e instanceof FirebaseAuthInvalidCredentialsException) {
                    showErrorDialog("Invalid code.");
                    pinCodeEditText.setText(null);
                } else {
                    showErrorDialog(e.getLocalizedMessage());
                }
            }
        });
    }*/

    /**
     * show a dialog that till that the reset process is done
     */
    private void showErrorDialog(String error, final int closeAppFlag) {
        // Create an AlertDialog.Builder and set the message, and click listeners
        // for the positive buttons on the dialog.
        AlertDialog.Builder builder = new AlertDialog.Builder(VerifyPhoneNumberActivity.this);
        builder.setMessage(error);
        builder.setTitle(error);

        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (dialog != null) {
                    if (closeAppFlag == INVALID_CODE_FLAG) {
                        dialog.dismiss();
                    } else if (closeAppFlag == INVALID_LINKING) {
                        Intent mainIntent = new Intent(VerifyPhoneNumberActivity.this, NotAuthEntryActivity.class);
                        //clear the application stack (clear all  former the activities)
                        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(mainIntent);
                        finish();
                    }
                }
            }
        });

        //create and show the alert dialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    /**
     * Link the layout element from XML to Java
     */
    private void initializeScreen() {
        resendTextView = (TextView) findViewById(R.id.resend_code_textView);
        continueVerfiyingButton = (Button) findViewById(R.id.continue_verifying_button);
        pinCodeEditText = (PinEntryEditText) findViewById(R.id.pin_code_editText);
    }
}