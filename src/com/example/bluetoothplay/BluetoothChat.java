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

package com.example.bluetoothplay;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.media.MediaPlayer;
import android.content.ContentValues;
import android.net.Uri;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.widget.SeekBar;
import android.media.AudioManager;
import java.lang.System;
import android.content.ComponentName;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This is the main Activity that displays the current chat session.
 */
public class BluetoothChat extends Activity {
    // Debugging
    private static final String TAG = "BluetoothChat";
    private static final boolean D = true;

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    // Layout Views
    private TextView mTitle;
    private TextView mOutText;
    private TextView mSyncText;
    private TextView mDelayText;
    private Button mPauseButton,mPlayButton,mConnectButton,mDiscoverableButton,mSendButton,mStopButton,mSyncButton,mPlayReceivedButton;
    private SeekBar mSeekBar,seekBar_delay;
    MediaPlayer player;
    private int mProgressStatus = 0;
    


    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Array adapter for the conversation thread
    private ArrayAdapter<String> mConversationArrayAdapter;
    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothChatService mChatService = null;
    String address ;
    long startTime,endTime;
    long difference;
    
    int max_vol;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.e(TAG, "+++ ON CREATE +++");

        // Set up the window layout
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.activity_main);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);

        // Set up the custom title
        mTitle = (TextView) findViewById(R.id.title_left_text);
        mTitle.setText(R.string.app_name);
        mTitle = (TextView) findViewById(R.id.title_right_text);

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        mOutText = (TextView) findViewById(R.id.TextView);
        mSyncText = (TextView) findViewById(R.id.text_sync);
        
        mDiscoverableButton = (Button) findViewById(R.id.button_discoverable);
        mDiscoverableButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // Ensure this device is discoverable by others
                ensureDiscoverable();
            }
        });
        
        mConnectButton = (Button) findViewById(R.id.button_connect);
        mConnectButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(BluetoothChat.this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
            }
        });
        
        
        player=MediaPlayer.create(BluetoothChat.this, R.raw.song);
        //player = new MediaPlayer();
       /* String data_source;
        try {
        	data_source = searchForBluetoothFolder()+"/America.mp3";
			player.setDataSource(data_source);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
        
        
        mSendButton = (Button) findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // Send the file over Bluetooth
            	
            	Send_Song_M();
            	/*Intent intent = new Intent();  
                intent.setAction(Intent.ACTION_SEND);  
                intent.setType("audio/*");

                String uri = "/storage/sdcard1/America.mp3";
                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(uri)));
                startActivity(intent);*/
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // Otherwise, setup the chat session
        } else {
            if (mChatService == null) setupChat();
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "+ ON RESUME +");

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
              // Start the Bluetooth chat services
              mChatService.start();
            }
        }
    }

    private void setupChat() {
        Log.d(TAG, "setupChat()");

       /* // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        mConversationView = (ListView) findViewById(R.id.in);
        mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
        mOutEditText = (EditText) findViewById(R.id.edit_text_out);
        mOutEditText.setOnEditorActionListener(mWriteListener);*/

        mPlayReceivedButton = (Button) findViewById(R.id.play_received);
        mPlayReceivedButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                player = new MediaPlayer();
                 String data_source;
                 try {
                 	data_source = searchForBluetoothFolder()+"/America.mp3";
         			player.setDataSource(data_source);
         		} catch (IllegalArgumentException e) {
         			// TODO Auto-generated catch block
         			e.printStackTrace();
         		} catch (SecurityException e) {
         			// TODO Auto-generated catch block
         			e.printStackTrace();
         		} catch (IllegalStateException e) {
         			// TODO Auto-generated catch block
         			e.printStackTrace();
         		} catch (IOException e) {
         			// TODO Auto-generated catch block
         			e.printStackTrace();
         		}
            	player.start();
                
            }
        });
        
        // Initialize the send button with a listener that for click events
        mPlayButton = (Button) findViewById(R.id.button_play);
        mPlayButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                sendMessage("PLAY");
            }
        });
        
        mPauseButton = (Button) findViewById(R.id.button_pause);
        mPauseButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                sendMessage("PAUSE");
            }
        });
        
        mStopButton = (Button) findViewById(R.id.button_stop);
        mStopButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                sendMessage("STOP");
            }
        });
       
        
        
		AudioManager audioManager = (AudioManager)getSystemService(getApplicationContext().AUDIO_SERVICE);
		
		max_vol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,max_vol/2,AudioManager.FLAG_SHOW_UI);
		
		
		
        mSeekBar = (SeekBar) findViewById(R.id.seekBar1);
        mSeekBar.setMax(max_vol);
        mSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener(){
        	int progress = 0;
        	
        	@Override
        	public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser) {
        		progress = progresValue;
        		sendMessage(Integer.toString(progress));
        	}

        	@Override
        	public void onStartTrackingTouch(SeekBar seekBar) {
        		//Toast.makeText(getApplicationContext(), "Started tracking seekbar", Toast.LENGTH_SHORT).show();
        	}

        	@Override
        	public void onStopTrackingTouch(SeekBar seekBar) {
        		//Toast.makeText(getApplicationContext(), "Stopped tracking seekbar", Toast.LENGTH_SHORT).show();
        	}
        });
        mDelayText = (TextView) findViewById(R.id.text_delay);

        seekBar_delay = (SeekBar) findViewById(R.id.seekBar_delay);
        seekBar_delay.setMax(500);
        seekBar_delay.setOnSeekBarChangeListener(new OnSeekBarChangeListener(){
        	int delay = 0;
        	int cur_pos = 0;
        	String delay_text;
        	
        	@Override
        	public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser) {
        		delay = progresValue/2-125;
        		cur_pos = player.getCurrentPosition();
        		player.pause();
        		player.seekTo(cur_pos+delay);
        		delay_text = Integer.toString(delay);
        		mDelayText.setText(delay_text);
        		player.start(); 
        		
        	}

        	@Override
        	public void onStartTrackingTouch(SeekBar seekBar) {
        		//Toast.makeText(getApplicationContext(), "Started tracking seekbar", Toast.LENGTH_SHORT).show();
        	}



        	@Override
        	public void onStopTrackingTouch(SeekBar seekBar) {
        		//Toast.makeText(getApplicationContext(), "Stopped tracking seekbar", Toast.LENGTH_SHORT).show();
        	
        		seekBar.setProgress(250);
        		
        	}
        });
        
        
        mSyncButton = (Button) findViewById(R.id.button_sync);
        mSyncButton.setOnClickListener(new OnClickListener(){
        	public void onClick(View v){
        		sendMessage("SYNC_START");
        		startTime = System.currentTimeMillis();
        	}
        });

        
        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(this, mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }
    
    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if (mChatService != null) mChatService.stop();
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }

    private void ensureDiscoverable() {
        if(D) Log.d(TAG, "ensure discoverable");
        if (mBluetoothAdapter.getScanMode() !=
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);

            // Reset out string buffer to zero
            mOutStringBuffer.setLength(0);
        }
    }

    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case BluetoothChatService.STATE_CONNECTED:
                    mTitle.setText(R.string.title_connected_to);
                    mTitle.append(mConnectedDeviceName);
                    break;
                case BluetoothChatService.STATE_CONNECTING:
                    mTitle.setText(R.string.title_connecting);
                    break;
                case BluetoothChatService.STATE_LISTEN:
                case BluetoothChatService.STATE_NONE:
                    mTitle.setText(R.string.title_not_connected);
                    break;
                }
                break;
            case MESSAGE_WRITE:
                byte[] writeBuf = (byte[]) msg.obj;
                // construct a string from the buffer
                String writeMessage = new String(writeBuf);
                mOutText.setText(writeMessage +"  Send");
                if (!writeMessage.equals("SYNC_START") && !writeMessage.equals("SYNC_STOP")){
                Play_Song(writeMessage);}
                break;
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
               mOutText.setText(readMessage+"  Received");
               Play_Song(readMessage);
                break;
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            }
        }
    };
    
    public void Play_Song(String play_pause){
    	
    	//Toast.makeText(getApplicationContext(), play_pause, Toast.LENGTH_SHORT).show();
    	long begin,dif;
    	
    	if (play_pause.equals("PLAY")){
    	begin = System.currentTimeMillis();
    	player.start();
    	dif = System.currentTimeMillis() - begin;

    	Toast.makeText(getApplicationContext(), "PLAY" + dif, Toast.LENGTH_SHORT).show();
    	}
    	
    	else if(play_pause.equals("PAUSE")){
	    	if(player!=null)
	    	{
	    	// player is the object of Media player
	    	player.pause();
	    	Toast.makeText(getApplicationContext(), "PAUSE", Toast.LENGTH_SHORT).show();
	
	    	}
    	}
    	

    	else if(play_pause.equals("STOP")){
	    	if(player!=null)
	    	{
	    	// player is the object of Media player
	    	player.stop();
	    	Toast.makeText(getApplicationContext(), "STOP", Toast.LENGTH_SHORT).show();
	    	}
    	}
    	
    	else if(play_pause.equals("SYNC_START")){
    		sendMessage("SYNC_STOP");
    	}
    	

    	else if(play_pause.equals("SYNC_STOP")){
    		difference = System.currentTimeMillis() - startTime;
    		mSyncText.setText(Long.toString(difference));
    	}
    	else{
    		//if(Integer.parseInt(play_pause) < 100){
    		AudioManager audioManager = (AudioManager)getSystemService(getApplicationContext().AUDIO_SERVICE);
    		audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,Integer.parseInt(play_pause),AudioManager.FLAG_SHOW_UI);
    		//}
    	}
    }
    
    public void Send_Song_M(){
    	
  	  String uri1 = "/storage/sdcard1/America3.mp3";
  	  String uri2 = "/storage/sdcard1/School.mp3";
  	  
    	ArrayList<Uri> uris = new ArrayList<Uri>();
    	
    	Intent intent = new Intent();  
    	

        intent.setAction(android.content.Intent.ACTION_SEND_MULTIPLE);

        intent.setType("audio/*");

            File file = new File(uri1);
            File file2 = new File(uri2);
            uris.add(Uri.fromFile(file));
            uris.add(Uri.fromFile(file2));

      

        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        startActivity(intent); 
    }
    
    public void Send_Song(){
    	ContentValues values = new ContentValues();
    	values.put(BluetoothShare.URI, Uri.fromFile(new File("sdcard/America.mp3")).toString());
    	values.put(BluetoothShare.DESTINATION, address);
    	values.put(BluetoothShare.DIRECTION, BluetoothShare.DIRECTION_OUTBOUND);
    	Long ts = System.currentTimeMillis();
    	values.put(BluetoothShare.TIMESTAMP, ts);
    	Uri contentUri = getContentResolver().insert(BluetoothShare.CONTENT_URI, values);
    }
    
   public void Send_Song_3(){
	  String uri1 = "/storage/sdcard1/America3.mp3";
	  String uri2 = "/storage/sdcard1/School.mp3";
    int currentapiVersion = android.os.Build.VERSION.SDK_INT;
    if (currentapiVersion >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
        Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
        sharingIntent.setType("audio/*");
        sharingIntent.setPackage("com.android.bluetooth");
        sharingIntent.putExtra(Intent.EXTRA_STREAM, uri1);
        sharingIntent.putExtra(Intent.EXTRA_STREAM, uri2);
        startActivity(Intent.createChooser(sharingIntent, "Share file"));
    	Toast.makeText(getApplicationContext(), "SEND -> 3", Toast.LENGTH_SHORT).show();
    } else {
        ContentValues values = new ContentValues();
        values.put(BluetoothShare.URI, uri1.toString());
        Toast.makeText(getBaseContext(), "URi : " + uri1, Toast.LENGTH_LONG).show();
        values.put(BluetoothShare.DESTINATION, address);
        values.put(BluetoothShare.DIRECTION,BluetoothShare.DIRECTION_OUTBOUND);
        Long ts = System.currentTimeMillis();
        values.put(BluetoothShare.TIMESTAMP, ts);
        getContentResolver().insert(BluetoothShare.CONTENT_URI,
                values);
    	Toast.makeText(getApplicationContext(), "SEND -> 5", Toast.LENGTH_SHORT).show();

    }
   }
   
  public void Send_Song_4(){
	  String uri1 = "/storage/sdcard1/America3.mp3";
	  String uri2 = "/storage/sdcard1/School.mp3";
	  
	  
  }
   
   public void  Send_Song_2(){
	   Intent intent = new Intent();  
	   intent.setAction(Intent.ACTION_SEND);  
	   intent.setType("audio/*");

	   String uri = "/storage/sdcard1/America3.mp3";
	   intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(uri)));
	   startActivity(intent);
   }
	/*Intent intent = new Intent();  
   intent.setAction(Intent.ACTION_SEND);  
   intent.setType("audio/*");

   String uri = "/storage/sdcard1/America.mp3";
   intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(uri)));
   startActivity(intent);*/

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                // Get the device MAC address
                address = data.getExtras()
                                     .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                // Get the BLuetoothDevice object
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                // Attempt to connect to the device
                mChatService.connect(device);
            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a chat session
                setupChat();
            } else {
                // User did not enable Bluetooth or an error occured
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
    
    public List<File> folderSearchBT(File src, String folder)
            throws FileNotFoundException {

        List<File> result = new ArrayList<File>();

        File[] filesAndDirs = src.listFiles();
        List<File> filesDirs = Arrays.asList(filesAndDirs);

        for (File file : filesDirs) {
            result.add(file); // always add, even if directory
            if (!file.isFile()) {
                List<File> deeperList = folderSearchBT(file, folder);
                result.addAll(deeperList);
            }
        }
        return result;
    }
    
    
    public String searchForBluetoothFolder() {

        String splitchar = "/";
        File root = Environment.getExternalStorageDirectory();
        List<File> btFolder = null;
        String bt = "bluetooth";
        try {
            btFolder = folderSearchBT(root, bt);
        } catch (FileNotFoundException e) {
            Log.e("FILE: ", e.getMessage());
        }

        for (int i = 0; i < btFolder.size(); i++) {

            String g = btFolder.get(i).toString();

            String[] subf = g.split(splitchar);

            String s = subf[subf.length - 1].toUpperCase();

            boolean equals = s.equalsIgnoreCase(bt);

            if (equals)
                return g;
        }
        return null; // not found
    }

}