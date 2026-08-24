-- KEYS[1] = reservations:pending-expiration
-- KEYS[2] = stream:reservations
--
-- ARGV[1] = agora, em epoch milissegundos
-- ARGV[2] = tamanho maximo do lote processado nesta chamada

local expiredIds = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2])
local expiredCount = 0

for i = 1, #expiredIds do
    local reservationId = expiredIds[i]
    local reservationKey = 'reservation:' .. reservationId
    local hash = redis.call('HGETALL', reservationKey)

    if #hash > 0 then
        local data = {}
        for j = 1, #hash, 2 do
            data[hash[j]] = hash[j + 1]
        end

        if data['status'] == 'PENDING' then
            local eventId = data['eventId']
            local quantity = tonumber(data['quantity'])
            local availabilityKey = 'event:' .. eventId .. ':available'

            redis.call('HSET', reservationKey, 'status', 'EXPIRED')
            redis.call('INCRBY', availabilityKey, quantity)
            redis.call('XADD', KEYS[2], '*',
                'reservationId', reservationId,
                'action', 'EXPIRE'
            )
            expiredCount = expiredCount + 1
        end
    end

    redis.call('ZREM', KEYS[1], reservationId)
end

return { 'EXPIRED', tostring(expiredCount) }
