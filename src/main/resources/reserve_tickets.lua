-- KEYS[1] = event:{eventId}:available
-- KEYS[2] = idempotency:{idempotencyKey}
-- KEYS[3] = reservation:{reservationId}
-- KEYS[4] = stream:reservations
-- KEYS[5] = reservations:pending-expiration
--
-- ARGV[1] = reservationId (UUID gerado pela aplicacao Kotlin)
-- ARGV[2] = eventId
-- ARGV[3] = userId
-- ARGV[4] = quantity
-- ARGV[5] = expiresAt (ISO-8601, calculado pela aplicacao)
-- ARGV[6] = idempotencyTtlSeconds
-- ARGV[7] = idempotencyKey (valor original, para o payload do stream)
-- ARGV[8] = expiresAt em epoch milissegundos (score do ZSET de rastreio de expiracao)

local existingId = redis.call('GET', KEYS[2])
if existingId then
    return { 'IDEMPOTENT', existingId }
end

local available = tonumber(redis.call('GET', KEYS[1]))
local quantity = tonumber(ARGV[4])

if available == nil or available < quantity then
    return { 'INSUFFICIENT_STOCK' }
end

redis.call('DECRBY', KEYS[1], quantity)

redis.call('HSET', KEYS[3],
    'eventId', ARGV[2],
    'userId', ARGV[3],
    'quantity', ARGV[4],
    'status', 'PENDING',
    'expiresAt', ARGV[5]
)

redis.call('ZADD', KEYS[5], ARGV[8], ARGV[1])

redis.call('XADD', KEYS[4], '*',
    'reservationId', ARGV[1],
    'eventId', ARGV[2],
    'userId', ARGV[3],
    'quantity', ARGV[4],
    'status', 'PENDING',
    'expiresAt', ARGV[5],
    'idempotencyKey', ARGV[7]
)

redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[6])

return { 'CREATED', ARGV[1] }
