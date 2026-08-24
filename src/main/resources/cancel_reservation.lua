-- KEYS[1] = reservation:{reservationId}
-- KEYS[2] = stream:reservations
-- KEYS[3] = reservations:pending-expiration
--
-- ARGV[1] = reservationId

local hash = redis.call('HGETALL', KEYS[1])
if #hash == 0 then
    return { 'NOT_FOUND' }
end

local data = {}
for i = 1, #hash, 2 do
    data[hash[i]] = hash[i + 1]
end

local status = data['status']
if status == 'CANCELLED' then
    return { 'ALREADY_CANCELLED' }
end
if status == 'EXPIRED' then
    return { 'ALREADY_EXPIRED' }
end

local eventId = data['eventId']
local quantity = tonumber(data['quantity'])
local availabilityKey = 'event:' .. eventId .. ':available'

redis.call('HSET', KEYS[1], 'status', 'CANCELLED')
redis.call('INCRBY', availabilityKey, quantity)
redis.call('ZREM', KEYS[3], ARGV[1])

redis.call('XADD', KEYS[2], '*',
    'reservationId', ARGV[1],
    'action', 'CANCEL'
)

return { 'CANCELLED' }
