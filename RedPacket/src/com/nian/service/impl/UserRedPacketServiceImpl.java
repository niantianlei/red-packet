package com.nian.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.nian.dao.RedPacketDao;
import com.nian.dao.UserRedPacketDao;
import com.nian.pojo.RedPacket;
import com.nian.pojo.UserRedPacket;
import com.nian.service.RedisRedPacketService;
import com.nian.service.UserRedPacketService;

import redis.clients.jedis.Jedis;

@Service
public class UserRedPacketServiceImpl implements UserRedPacketService {

	@Autowired
	private UserRedPacketDao userRedPacketDao = null;

	@Autowired
	private RedPacketDao redPacketDao = null;

	// 失败
	private static final int FAILED = 0;
	
	/*
	 * 同RedPacketServiceImpl一样，也要配置事务，保证所有操作都是在一个事务中完成的。
	 * @see com.nian.service.UserRedPacketService#grapRedPacket(java.lang.Long, java.lang.Long)
	 */
	@Override
	@Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED)
	public int grapRedPacket(Long redPacketId, Long userId) {
		//获取红包信息
		//超发模拟
		//RedPacket redPacket = redPacketDao.getRedPacket(redPacketId);
		// 悲观锁
		RedPacket redPacket = redPacketDao.getRedPacketForUpdate(redPacketId);
		// 当前小红包库存大于0
		if (redPacket.getStock() > 0) {
			redPacketDao.decreaseRedPacket(redPacketId);
			// 生成抢红包信息
			UserRedPacket userRedPacket = new UserRedPacket();
			userRedPacket.setRedPacketId(redPacketId);
			userRedPacket.setUserId(userId);
			userRedPacket.setAmount(redPacket.getUnitAmount());
			userRedPacket.setNote("抢红包 " + redPacketId);
			// 插入抢红包信息
			int result = userRedPacketDao.grapRedPacket(userRedPacket);
			return result;
		}
		// 失败返回
		return FAILED;
	}

/*	// 乐观锁，无重入
	@Override
	@Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED)
	public int grapRedPacketForVersion(Long redPacketId, Long userId) {
		// 获取红包信息,注意version值
		RedPacket redPacket = redPacketDao.getRedPacket(redPacketId);
		// 当前小红包库存大于0
		if (redPacket.getStock() > 0) {
			// 传入线程保存的version旧值给SQL判断，是否有其他线程修改过数据
			int update = redPacketDao.decreaseRedPacketForVersion(redPacketId, redPacket.getVersion());
			// 为0说明减库存失败（版本号不一致），update为影响的行数。
			// 如果没有数据更新，则说明其他线程已经修改过数据，本次抢红包失败
			if (update == 0) {
				return FAILED;
			}
			// 生成抢红包信息
			UserRedPacket userRedPacket = new UserRedPacket();
			userRedPacket.setRedPacketId(redPacketId);
			userRedPacket.setUserId(userId);
			userRedPacket.setAmount(redPacket.getUnitAmount());
			userRedPacket.setNote("抢红包 " + redPacketId);
			// 插入抢红包信息
			int result = userRedPacketDao.grapRedPacket(userRedPacket);
			return result;
		}
		// 失败返回
		return FAILED;
	}*/

	// 乐观锁，按时间戳重入
/*	@Override
	@Transactional(isolation = Isolation.READ_COMMITTED, propagation =
	Propagation.REQUIRED)
	public int grapRedPacketForVersion(Long redPacketId, Long userId) {
		// 记录开始时间
		long start = System.currentTimeMillis();
		// 无限循环，等待成功或者时间满100毫秒退出
		while (true) {
			// 获取循环当前时间
			long end = System.currentTimeMillis();
			// 当前时间已经超过100毫秒，返回失败
			if (end - start > 100) {
				return FAILED;
			}
			// 获取红包信息,注意version值
			RedPacket redPacket = redPacketDao.getRedPacket(redPacketId);
			// 当前小红包库存大于0
			if (redPacket.getStock() > 0) {
				// 传入线程保存的version旧值给SQL判断，是否有其他线程修改过数据
				int update = redPacketDao.decreaseRedPacketForVersion(redPacketId,
				redPacket.getVersion());
				// 如果没有数据更新，则说明其他线程已经修改过数据，则重新抢夺
				if (update == 0) {
					continue;
				}
				// 生成抢红包信息
				UserRedPacket userRedPacket = new UserRedPacket();
				userRedPacket.setRedPacketId(redPacketId);
				userRedPacket.setUserId(userId);
				userRedPacket.setAmount(redPacket.getUnitAmount());
				userRedPacket.setNote("抢红包 " + redPacketId);
				// 插入抢红包信息
				int result = userRedPacketDao.grapRedPacket(userRedPacket);
				return result;
			} else {
				// 一旦没有库存，则马上返回
				return FAILED;
			}
		}
	}*/

	// 乐观锁，按次数重入
	@Override
	@Transactional(isolation = Isolation.READ_COMMITTED, propagation =
	Propagation.REQUIRED)
	public int grapRedPacketForVersion(Long redPacketId, Long userId) {
		for (int i = 0; i < 5; i++) {
			// 获取红包信息，注意version值
			RedPacket redPacket = redPacketDao.getRedPacket(redPacketId);
			// 当前小红包库存大于0
			if (redPacket.getStock() > 0) {
				// 再次传入线程保存的version旧值给SQL判断，是否有其他线程修改过数据
				int update = redPacketDao.decreaseRedPacketForVersion(redPacketId,
				redPacket.getVersion());
				// 如果没有数据更新，则说明其他线程已经修改过数据，则重新抢夺
				if (update == 0) {
					continue;
				}
				// 生成抢红包信息
				UserRedPacket userRedPacket = new UserRedPacket();
				userRedPacket.setRedPacketId(redPacketId);
				userRedPacket.setUserId(userId);
				userRedPacket.setAmount(redPacket.getUnitAmount());
				userRedPacket.setNote("抢红包 " + redPacketId);
				// 插入抢红包信息
				int result = userRedPacketDao.grapRedPacket(userRedPacket);
				return result;
			} else {
				// 一旦没有库存，则马上返回
				return FAILED;
			}
		}
		return FAILED;
	}

	
	@Autowired
	private RedisTemplate redisTemplate = null;

	@Autowired
	private RedisRedPacketService redisRedPacketService = null;
	/*
	 * Lua流程：1判断是否存在可抢红包，如果没有返回0结束流程
	 * 2有红包库存，库存-1，然后重新设置库存
	 * 3将抢红包数据保存到Redis的列表中，列表的key为red_packet_list_{id}
	 * 4如果库存为0，返回2.说明已经抢完，此时需要将Redis列表数据存储到数据库，
	 * 5如果库存不为0，返回1，说明抢红包信息保存成功。
	 */
	// Lua脚本
	String script = "local listKey = 'red_packet_list_'..KEYS[1] \n" 
	+ "local redPacket = 'red_packet_'..KEYS[1] \n"
			+ "local stock = tonumber(redis.call('hget', redPacket, 'stock')) \n" 
			+ "if stock <= 0 then return 0 end \n"
			+ "stock = stock -1 \n" 
			+ "redis.call('hset', redPacket, 'stock', tostring(stock)) \n"
			+ "redis.call('rpush', listKey, ARGV[1]) \n" 
			+ "if stock == 0 then return 2 end \n" 
			+ "return 1 \n";

	// 在缓存LUA脚本后，使用该变量保存Redis返回的32位的SHA1编码，使用它去执行缓存的LUA脚本[加入这句话]
	String sha1 = null;

	@Override
	public Long grapRedPacketByRedis(Long redPacketId, Long userId) {
		// 当前抢红包用户和日期信息
		String args = userId + "-" + System.currentTimeMillis();
		Long result = null;
		// 获取底层Redis操作对象
		Jedis jedis = (Jedis) redisTemplate.getConnectionFactory().getConnection().getNativeConnection();
		try {
			// 如果脚本没有加载过，那么进行加载，这样就会返回一个sha1编码
			if (sha1 == null) {
				sha1 = jedis.scriptLoad(script);
			}
			// 执行脚本，返回结果
			Object res = jedis.evalsha(sha1, 1, redPacketId + "", args);
			result = (Long) res;
			// 返回2时为最后一个红包，此时将抢红包信息通过异步保存到数据库中
			if (result == 2) {
				// 获取单个小红包金额
				String unitAmountStr = jedis.hget("red_packet_" + redPacketId, "unit_amount");
				// 触发保存数据库操作
				Double unitAmount = Double.parseDouble(unitAmountStr);
				System.err.println("thread_name = " + Thread.currentThread().getName());
				//将数据保存到数据库
				redisRedPacketService.saveUserRedPacketByRedis(redPacketId, unitAmount);
			}
		} finally {
			// 确保jedis顺利关闭
			if (jedis != null && jedis.isConnected()) {
				jedis.close();
			}
		}
		return result;
	}
}
