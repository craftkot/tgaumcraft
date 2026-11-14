import io,struct,zlib,gzip,json,sys,pathlib,uuid
class NBT:
 def __init__(self,data):self.f=io.BytesIO(data)
 def num(self,kind):return struct.unpack('>'+kind,self.f.read(struct.calcsize('>'+kind)))[0]
 def string(self):return self.f.read(self.num('H')).decode()
 def value(self,t):
  if t in (1,2,3,4,5,6):return self.num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
  if t==7:return list(self.f.read(self.num('i')))
  if t==8:return self.string()
  if t==9:
   kind=self.num('b');return [self.value(kind) for _ in range(self.num('i'))]
  if t==10:
   out={}
   while True:
    kind=self.num('b')
    if kind==0:return out
    key=self.string();out[key]=self.value(kind)
  if t in(11,12):return [self.num('i' if t==11 else 'q') for _ in range(self.num('i'))]
  raise ValueError(t)
 def root(self):kind=self.num('b');self.string();return self.value(kind)
world=pathlib.Path(sys.argv[1]);phase=sys.argv[2]
chunks={}
def tile(x,y,z):
 cx,cz=x//16,z//16
 if (cx,cz) not in chunks:
  b=(world/f'region/r.{cx//32}.{cz//32}.mca').read_bytes()
  entry=4*((cx%32)+(cz%32)*32);offset=int.from_bytes(b[entry:entry+3],'big')*4096
  assert offset, f'Chunk {cx},{cz} missing on disk'
  size=int.from_bytes(b[offset:offset+4],'big');compression=b[offset+4];raw=b[offset+5:offset+4+size]
  raw={1:gzip.decompress,2:zlib.decompress,3:lambda v:v}[compression](raw)
  chunks[cx,cz]=NBT(raw).root()
 return next(t for t in chunks[cx,cz]['block_entities'] if (t['x'],t['y'],t['z'])==(x,y,z))
center=tile(8,126,8);matrix=tile(8,128,8);sides=[tile(5,126,8),tile(11,126,8)]
jars=[tile(x,130,40) for x in (7,8,9)];mirrors=[tile(10,128,8),tile(8,128,40)]
properties=dict(line.split('=',1) for line in (world/'infusion-paid-test.properties').read_text().splitlines() if line and not line.startswith('#'))
assert properties['mirrors']=='true'
ritual=uuid.UUID(properties['ritual']);uid=[struct.unpack('>i',ritual.bytes[i:i+4])[0] for i in range(0,16,4)]
for a,b in (mirrors,mirrors[::-1]):
 assert a['linked'] and a['linkDim']=='minecraft:overworld'
 assert (a['linkX'],a['linkY'],a['linkZ'])==(b['x'],b['y'],b['z'])
assert mirrors[0]['instability']>0 and mirrors[1]['instability']==0
amounts=[j.get('Amount',0) for j in jars]
if phase=='partial':
 ledger=matrix['PortInfusionLedger'];assert matrix['crafting']==1 and ledger['Format']==3 and ledger['Paid']==8 and ledger['RitualId']==uid
 assert ledger['PlannedOutput']['Items'][0]['id']=='thaumcraft:crystal_aer'
 assert all(len(t['Items'])==1 for t in [center,*sides])
 assert amounts==[2,10,5]
else:
 assert phase=='complete' and matrix['crafting']==0 and 'PortInfusionLedger' not in matrix
 assert amounts==[0,0,0]
 assert center['Items'][0]['id']=='thaumcraft:crystal_aer' and center['Items'][0]['count']==1
 assert center['PortInfusionOutputRitual']==uid
 for i,t in enumerate(sides):assert not t.get('Items',[]) and t['PortInfusionRemovalRitual']==uid and t['PortInfusionRemovalBit']==1<<i
print(json.dumps({'phase':phase,'ritual':str(ritual),'matrix':matrix,'center':center,'sides':sides,'jars':jars,'mirrors':mirrors,'verified_remote_jar_amounts':amounts},indent=2))
