package com.jgh.androidssh.sshutils;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.widget.EditText;
import java.io.File;
import java.io.IOException;
import java.lang.InterruptedException;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;
import com.jgh.androidssh.FileListActivity;
import com.jgh.androidssh.adapters.RemoteFileListAdapter;

import java.util.Properties;
import java.util.Vector;

/**
 * Controller for Jsch SSH sessions. All SSH
 * connections are run through this class.
 * <p/>
 * Created by Jon Hough on 3/25/14.
 */
public class SessionController {

    private static final String TAG = "SessionController";

    /**
     * JSch Session
     */
    private Session mSession;
    /**
     * JSch UserInfo
     */
    private SessionUserInfo mSessionUserInfo;
    /**
     * Thread for background tasks
     */
    private Thread mThread;
    /**
     * Controls SFTP interface
     */
    private SftpController mSftpController;

    /**
     * Controls Shell interface
     */
    private ShellController mShellController;

    /**
     *
     */
    private ConnectionStatusListener mConnectStatusListener;
    /**
     * Instance
     */
    private static SessionController sSessionController;


    private SessionController() {
    }

    public static SessionController getSessionController() {
        if (sSessionController == null) {
            sSessionController = new SessionController();
        }
        return sSessionController;
    }


    public Session getSession() {
        return mSession;
    }

    /**
     * @param sessionUserInfo The SessionUserInfo to be used by all SSH channels.
     */
    private SessionController(SessionUserInfo sessionUserInfo) {
        mSessionUserInfo = sessionUserInfo;
        connect();

    }

    public void setUserInfo(SessionUserInfo sessionUserInfo) {
        mSessionUserInfo = sessionUserInfo;
    }


    public void connect() {
        if (mSession == null) {
            mThread = new Thread(new SshRunnable());
            mThread.start();
        } else if (!mSession.isConnected()) {
            mThread = new Thread(new SshRunnable());
            mThread.start();
        }
    }

    public SftpController getSftpController(){
        return mSftpController;
    }

    public void setConnectionStatusListener(ConnectionStatusListener csl) {
        mConnectStatusListener = csl;
    }


    /**
     * Uploads files to remote server.
     *
     * @param files
     * @param spm
     */
    public void uploadFiles(File[] files, SftpProgressMonitor spm) {
        if (mSftpController == null) {
            mSftpController = new SftpController();

        }
        mSftpController.new UploadTask(mSession, files, spm).execute();
    }


    /**
     * Downloads file from remote server.
     *
     * @param srcPath
     * @param out
     * @param spm
     * @return
     * @throws JSchException
     * @throws SftpException
     */
    public boolean downloadFile(String srcPath, String out, SftpProgressMonitor spm) throws JSchException, SftpException {
        if (mSftpController == null) {
            mSftpController = new SftpController();

        }
        mSftpController.new DownloadTask(mSession, srcPath, out, spm).execute();
        return true;
    }

    /**
     * Lists the files in the current directory on remote server.
     * @param taskCallbackHandler
     * @param path
     * @throws JSchException
     * @throws SftpException
     */
    public void listRemoteFiles(TaskCallbackHandler taskCallbackHandler, String path) throws JSchException, SftpException {

        if (mSession == null || !mSession.isConnected()) {
            return;
        }

        if (mSftpController == null) {
            mSftpController = new SftpController();

        }
        //list the files.
        mSftpController.lsRemoteFiles(mSession, taskCallbackHandler, path);


    }


    /**
     * Disconnects session and all channels.
     * @throws IOException
     */
    public void disconnect() throws IOException {
        if (mSession != null) {
            if(mSftpController != null){
                mSftpController.disconnect();
            }
            if(mShellController != null){
                mShellController.disconnect();
            }
            mSession.disconnect();

            if (mConnectStatusListener != null)
                mConnectStatusListener.onDisconnected();
        }
        if (mThread != null && mThread.isAlive()) {
            try {
                mThread.join();
            } catch (InterruptedException e) {
                //
            }
        }
    }


    /**
     * Execute command on remote server. If SSH is not open, SSH shell will be opened and
     * command executed.
     *
     * @param command
     * @return
     */
    public boolean executeCommand(Handler handler, EditText editText, ExecTaskCallbackHandler callback, String command) {
        if (mSession == null || !mSession.isConnected()) {
            return false;
        } else {

            if (mShellController == null) {
                mShellController = new ShellController();

                try {
                    mShellController.openShell(getSession(), handler, editText);
                } catch (Exception e) {
                    Log.e(TAG, "Shell open exception "+e.getMessage());
                    //TODO fix general exception catching
                }
            }

            synchronized (mShellController) {
                mShellController.writeToOutput(command);
            }
        }

        return true;
    }


    /**
     * Runnable for beginning session.
     */
    public class SshRunnable implements Runnable {

        public void run() {
            JSch jsch = new JSch();
            mSession = null;
            try {
                mSession = jsch.getSession(mSessionUserInfo.getUser(), mSessionUserInfo.getHost(),
                        22); // port 22

                mSession.setUserInfo(mSessionUserInfo);

                Properties properties = new Properties();
                properties.setProperty("StrictHostKeyChecking", "no");
                mSession.setConfig(properties);
                mSession.connect();

            } catch (JSchException jex) {
                Log.e(TAG, "JschException: " + jex.getMessage() +
                        ", Fail to get session " + mSessionUserInfo.getUser() +
                        ", " + mSessionUserInfo.getHost());
            } catch (Exception ex) {
                Log.e(TAG, "Exception:" + ex.getMessage());
            }

            Log.d("SessionController", "Session !" + mSession.isConnected());

            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {

                        //keep track of connection status
                        try {
                            Thread.sleep(2000);
                            if (mConnectStatusListener != null) {
                                if (mSession.isConnected()) {
                                    mConnectStatusListener.onConnected();
                                } else mConnectStatusListener.onDisconnected();
                            }
                        } catch (InterruptedException e) {

                        }
                    }
                }
            }).start();
        }
    }






}
