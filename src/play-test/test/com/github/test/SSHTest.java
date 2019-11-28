package com.github.test;

import net.schmizz.sshj.DefaultConfig;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.userauth.method.AbstractAuthMethod;
import net.schmizz.sshj.xfer.scp.SCPFileTransfer;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class SSHTest {

    public static void main(String[] args) throws IOException {

        final SSHClient ssh = new SSHClient();
        ssh.loadKnownHosts();
        System.out.println("connect-01");
        ssh.setConnectTimeout(30 * 1000);
        ssh.setTimeout(30 * 1000);
        ssh.setRemoteCharset(Charset.forName("GBK"));
        ssh.connect("sftp.95516.com");
        try {

            System.out.println("connect-02");

            ssh.authPassword("agentpay1234","agent2018");

            System.out.println("connect-03");

            final SFTPClient sftp = ssh.newSFTPClient();

            System.out.println("downloadin-01");

            sftp.get("/GLNFSAPS/FLNFS/04375852/20181114/IND18111432ACOMA","/Users/jessie/Workspace/子商户信息/INN15090588ZM_802310048993424");

            System.out.println("downloadin-02");

        } finally {
            System.out.println("downloadin-03");
            ssh.disconnect();
        }
    }


}
