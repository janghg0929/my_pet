package com.mypet;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** 단일 실행 보장 (my_pet_single_instance.py 포팅). 윈도우/맥 모두 파일 잠금을 사용한다. */
public final class SingleInstance {
	private static FileChannel channel;
	private static FileLock lock;

	private SingleInstance() {
	}

	/**
	 * 단일 실행 잠금을 획득. 이미 실행 중이면 false.
	 * waitMillis 동안 재시도한다 (업데이트 후 재시작 시 이전 프로세스가 끝나길 기다리기 위함).
	 */
	public static synchronized boolean acquire(long waitMillis) {
		if (lock != null) {
			return true;
		}
		Path lockPath = Constants.appDataDir().resolve("my_pet.lock");
		try {
			Files.createDirectories(lockPath.getParent());
			channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		} catch (IOException | RuntimeException e) {
			// 잠금 파일을 못 만들면 실행을 막지 않음
			return true;
		}

		long deadline = System.currentTimeMillis() + Math.max(0, waitMillis);
		while (true) {
			try {
				lock = channel.tryLock();
			} catch (IOException | OverlappingFileLockException e) {
				lock = null;
			}
			if (lock != null) {
				return true;
			}
			if (System.currentTimeMillis() >= deadline) {
				break;
			}
			try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		try {
			channel.close();
		} catch (IOException ignored) {
			// 무시
		}
		channel = null;
		return false;
	}

	/** 단일 실행 잠금을 해제 (업데이트 후 재시작 직전에 호출). */
	public static synchronized void release() {
		try {
			if (lock != null) {
				lock.release();
			}
			if (channel != null) {
				channel.close();
			}
		} catch (IOException ignored) {
			// 무시
		}
		lock = null;
		channel = null;
	}
}
