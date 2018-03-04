package com.nian.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.nian.dao.RedPacketDao;
import com.nian.pojo.RedPacket;
import com.nian.service.RedPacketService;

@Service
public class RedPacketServiceImpl implements RedPacketService {
	
	@Autowired
	private RedPacketDao  redPacketDao = null;

	/*
	 * 配置了事务注解@Transactional，让程序能够在事务中运行，以保证数据的一致性，这里采用的是读已提交的隔离级别，
	 * 不采用更高级别，主要是考虑到并发性能，而对于传播行为则用默认的REQUIRED，没有事务创建新事务，有则沿用。
	 * @see com.nian.service.RedPacketService#getRedPacket(java.lang.Long)
	 */
	@Override
	@Transactional(isolation=Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED)
	public RedPacket getRedPacket(Long id) {
		return redPacketDao.getRedPacket(id);
	}

	@Override
	@Transactional(isolation=Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED)
	public int decreaseRedPacket(Long id) {
		return redPacketDao.decreaseRedPacket(id);
	}

}