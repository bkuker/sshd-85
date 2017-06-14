package com.billkuker.sshd.sshd85;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TheTest {
	private static final Logger log = LoggerFactory.getLogger(TheTest.class);

	private static final int SSH_SERVER_PORT = 1123;
	private static final int TEST_SERVER_PORT = 1124;
	private static int FORWARD_PORT_START = 1126;

	private static final String PAYLOAD = "This is significantly longer Test Data. This is significantly "
			+ "longer Test Data. This is significantly longer Test Data. This is significantly "
			+ "longer Test Data. This is significantly longer Test Data. This is significantly "
			+ "longer Test Data. This is significantly longer Test Data. This is significantly "
			+ "longer Test Data. This is significantly longer Test Data. This is significantly " + "longer Test Data!";

	/**
	 * Starts an SSH Server
	 */
	@BeforeClass
	public static void startSshServer() throws IOException {
		log.info("Starting SSHD...");
		SshServer server = SshServer.setUpDefaultServer();
		server.setPasswordAuthenticator((u, p, s) -> true);
		server.setTcpipForwardingFilter(new AcceptAllForwardingFilter());
		server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
		server.setPort(SSH_SERVER_PORT);
		server.start();
		log.info("SSHD Running on port {}", server.getPort());
	}

	/**
	 * Start a server to forward to.
	 * 
	 * This server sends PAYLOAD and then disconnects.
	 */
	@BeforeClass
	public static void startTestServer() throws IOException {
		new Thread(() -> {
			try (final ServerSocket ss = new ServerSocket(TEST_SERVER_PORT)) {
				log.info("Test server listening on {}", ss.getLocalPort());
				while (true) {
					Socket s = ss.accept();
					log.info("Got a connection");
					s.getOutputStream().write(PAYLOAD.getBytes());
					s.getOutputStream().flush();
					s.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}).start();
	}

	@Before
	public void createClient() throws IOException {
		log.info("Creating SSH Client...");
		SshClient client = SshClient.setUpDefaultClient();
		client.start();
		session = client.connect("user", "localhost", SSH_SERVER_PORT).verify(1000).getSession();
		session.addPasswordIdentity("foo");
		session.auth().verify(1000);
		log.info("SSH Client connected to server.");
	}

	ClientSession session;

	/**
	 * Connect to test server via port forward and read real quick with one big
	 * buffer
	 */
	@Test
	public void readFastPF() throws Exception {
		int sinkPort = FORWARD_PORT_START++;
		log.debug("{}", session);
		session.startLocalPortForwarding(new SshdSocketAddress("localhost", sinkPort),
				new SshdSocketAddress("localhost", TEST_SERVER_PORT));
		log.debug("Connecting to {}", sinkPort);
		try (Socket s = new Socket("localhost", sinkPort)) {
			byte b1[] = new byte[PAYLOAD.length()];
			int read1 = s.getInputStream().read(b1);
			log.info("Got {} bytes from the server: {}", read1, new String(b1, 0, read1));
			Assert.assertEquals(PAYLOAD, new String(b1, 0, read1));
		}

	}

	/**
	 * Connect to test server via port forward and read with 2 buffers and a
	 * pause in between
	 */
	@Test
	public void readSlowPF() throws Exception {
		int sinkPort = FORWARD_PORT_START++;
		log.debug("{}", session);
		session.startLocalPortForwarding(new SshdSocketAddress("localhost", sinkPort),
				new SshdSocketAddress("localhost", TEST_SERVER_PORT));
		log.debug("Connecting to {}", sinkPort);
		try (Socket s = new Socket("localhost", sinkPort)) {
			byte b1[] = new byte[PAYLOAD.length() / 2];
			byte b2[] = new byte[PAYLOAD.length()];

			int read1 = s.getInputStream().read(b1);
			log.info("Got {} bytes from the server: {}", read1, new String(b1, 0, read1));

			Thread.sleep(50);

			// THE FOLLOWING READ FAILS
			int read2 = s.getInputStream().read(b2);
			log.info("Got {} bytes from the server: {}", read2, new String(b2, 0, read2));

			Assert.assertEquals(PAYLOAD, new String(b1, 0, read1) + new String(b2, 0, read2));
		}
	}

	/**
	 * Connect to test server directly and read with 2 buffers and a pause in
	 * between
	 */
	@Test
	public void readSlowDirect() throws Exception {
		log.debug("Connecting to {}", TEST_SERVER_PORT);
		try (Socket s = new Socket("localhost", TEST_SERVER_PORT)) {
			byte b1[] = new byte[PAYLOAD.length() / 2];
			byte b2[] = new byte[PAYLOAD.length()];
			int read1 = s.getInputStream().read(b1);
			log.info("Got {} bytes from the server: {}", read1, new String(b1, 0, read1));
			Thread.sleep(50);
			int read2 = s.getInputStream().read(b2);
			log.info("Got {} bytes from the server: {}", read2, new String(b2, 0, read1));

			Assert.assertEquals(PAYLOAD, new String(b1, 0, read1) + new String(b2, 0, read2));
		}
	}
}
