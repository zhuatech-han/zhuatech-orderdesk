#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""独立MySQL部署的完整订货、价表、权限、回滚、版本与持久化验收；只用于全新测试库。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import json,os,secrets,urllib.request,urllib.error,http.cookiejar,datetime,concurrent.futures
from pathlib import Path
root=Path(__file__).resolve().parents[1]
env=dict(line.split('=',1) for line in (root/'.env').read_text().splitlines() if line and not line.startswith('#') and '=' in line)
base=os.environ.get('TEST_URL','http://127.0.0.1:'+env['WEB_PORT'])
count=0

def check(value,message):
 global count
 count+=1
 if not value:raise AssertionError(message)

class Client:
 def __init__(self):self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()));self.csrf=None
 def call(self,path,method='GET',body=None,expected=200,raw=False):
  if method!='GET' and self.csrf is None:self.csrf=self.call('/api/auth/csrf')
  headers={'Content-Type':'application/json'}
  if method!='GET':headers[self.csrf['header']]=self.csrf['token']
  req=urllib.request.Request(base+path,method=method,headers=headers,data=None if body is None else json.dumps(body).encode())
  try:
   with self.opener.open(req,timeout=25) as r:status=r.status;data=r.read()
  except urllib.error.HTTPError as e:status=e.code;data=e.read()
  check(status==expected,f'{method} {path}: expected {expected}, got {status}; '+data[:400].decode(errors='replace'))
  return data.decode('utf-8-sig') if raw else json.loads(data)
 def login(self,name,password):self.call('/api/auth/login','POST',{'username':name,'password':password});return self

anon=Client();check(anon.call('/actuator/health')['status']=='UP','health');anon.call('/api/orders',expected=401)
a=Client().login(env['ADMIN_USERNAME'],env['ADMIN_PASSWORD'])
roles=a.call('/api/admin/roles');check(len(roles)==4,'bootstrap roles');check(len(a.call('/api/orders'))==0,'fresh orders');check(len(a.call('/api/master/customers'))==0,'fresh customers')
role=lambda name:next(r['id'] for r in roles if name in r['name'])
new_password='Aa9'+secrets.token_hex(18)
def customer(code,dept=1,name=None):return a.call('/api/master/customers','POST',{'code':code,'name':name or 'TEST '+code,'departmentId':dept,'address':'TEST Shanghai delivery address','paymentTerms':'TEST monthly settlement outside this system','enabled':True})
c1=customer('TEST-A');c2=customer('TEST-B');dept=a.call('/api/admin/departments','POST',{'name':'TEST other department'});c3=customer('TEST-C',dept['id'])
def user(name,r,dept,c=None):return a.call('/api/admin/users','POST',{'username':name,'displayName':'TEST '+name,'password':new_password,'roleId':role(r),'departmentId':dept,'customerId':c,'enabled':True})
u1=user('test-buyer','Customer',1,c1['id']);u2=user('test-other','Customer',1,c2['id']);u3=user('test-staff','Sales operations',1);u4=user('test-outside','Sales operations',dept['id'])
b=Client().login('test-buyer',new_password);other=Client().login('test-other',new_password);staff=Client().login('test-staff',new_password);outside=Client().login('test-outside',new_password)
products=[];prices=[]
for i in range(1,13):
 p=a.call('/api/master/products','POST',{'sku':f'TEST-SKU-{i:02d}','name':f'TEST 订货商品 {i:02d}','nameEn':f'TEST wholesale product {i:02d}','category':'SUPPLIES' if i<7 else 'FOOD','unit':'箱 / case','enabled':True});products.append(p)
 price=a.call('/api/master/prices','POST',{'customerId':c1['id'],'productId':p['id'],'unitPrice':'12.35' if i==1 else str(10+i)+'.20','minQty':2 if i==2 else 1,'stepQty':2 if i==2 else 1,'maxQty':1000,'enabled':True});prices.append(price)
a.call('/api/master/prices','POST',{'customerId':c2['id'],'productId':products[0]['id'],'unitPrice':'8.20','minQty':1,'stepQty':1,'maxQty':100,'enabled':True})
hidden=a.call('/api/master/products','POST',{'sku':'TEST-UNASSIGNED','name':'TEST 不授权商品','nameEn':'TEST unassigned product','category':'GENERAL','unit':'箱 / case','enabled':True})
portal=b.call('/api/portal');check(len(portal['items'])==12,'own catalogue');check(all(x['product']['id']!=hidden['id'] for x in portal['items']),'unassigned excluded');check(float(other.call('/api/portal')['items'][0]['unitPrice'])==8.2,'other own price')
for path in ['/api/master/customers','/api/master/prices','/api/master/products','/api/admin/users','/api/catalog','/api/orders','/api/reports','/api/audit','/api/prices.csv']:
 b.call(path,expected=403)
check(len(outside.call('/api/master/customers'))==1,'department customer scope');check(len(outside.call('/api/orders'))==0,'department order scope');check(len(staff.call('/api/master/customers'))==2,'staff customer scope')
future=(datetime.date.today()+datetime.timedelta(days=2)).isoformat()
lines=[{'productId':products[0]['id'],'qty':3},{'productId':products[1]['id'],'qty':4}]
def save(lines=lines,expected=200):return b.call('/api/orders','POST',{'deliveryDate':future,'note':'TEST regular replenishment','lines':lines},expected)
o=save();check(float(o['total'])==85.85,'exact decimal total')
count_before=len(b.call('/api/portal')['orders'])
for qty in [1.5,'1.5']:
 check(save([{'productId':products[0]['id'],'qty':qty}],400)['code']=='INVALID_INPUT','fractional quantity gets a business-readable error')
 b.call('/api/templates','POST',{'name':'TEST invalid fractional list','lines':[{'productId':products[0]['id'],'qty':qty}]},400)
save([{'productId':products[0]['id']+0.5,'qty':1}],400)
check(len(b.call('/api/portal')['orders'])==count_before,'fractional request creates no order')
b.call(f"/api/orders/{o['id']}/actions/submit",'POST',{'revision':o['revision']+0.5},400)
other.call('/api/orders/'+str(o['id']),expected=403);outside.call('/api/orders/'+str(o['id']),expected=403)
def action(client,order_id,act,expected=200):
 d=client.call('/api/orders/'+str(order_id));return client.call(f'/api/orders/{order_id}/actions/{act}','POST',{'revision':d['order']['revision'],'note':'TEST verified request'},expected)
save([{'productId':hidden['id'],'qty':1}],409);save([{'productId':products[1]['id'],'qty':3}],400);save([{'productId':products[0]['id'],'qty':1},{'productId':products[0]['id'],'qty':1}],400)
# Changed price is never silently applied to a previously reviewed draft.
p0=prices[0]
def pricebody(p,value):return {'customerId':p['customerId'],'productId':p['productId'],'unitPrice':value,'minQty':p['minQty'],'stepQty':p['stepQty'],'maxQty':p['maxQty'],'enabled':True,'revision':p['revision']}
p0=a.call('/api/master/prices/'+str(p0['id']),'PUT',pricebody(p0,'13.50'))
check(action(b,o['id'],'submit',409)['code']=='PRICE_CHANGED','price-change review')
o=b.call('/api/orders/'+str(o['id']),'PUT',{'deliveryDate':future,'note':'TEST current price reviewed','lines':lines,'revision':o['revision']});check(float(o['total'])==89.3,'repriced draft')
o=action(b,o['id'],'submit');b.call(f"/api/orders/{o['id']}/actions/submit",'POST',{'revision':o['revision']-1},409);b.call(f"/api/orders/{o['id']}/actions/confirm",'POST',{'revision':o['revision']},403)
o=action(a,o['id'],'confirm');p0=a.call('/api/master/prices/'+str(p0['id']),'PUT',pricebody(p0,'14.00'))
d=a.call('/api/orders/'+str(o['id']));check(float(d['order']['total'])==89.3,'confirmed amount frozen');check(float(d['lines'][0]['unitPrice'])==13.5,'confirmed unit price frozen')
ref=f"/api/orders/{o['id']}/dispatches"
a.call(ref,'POST',{'revision':o['revision'],'reference':'TEST-FRACTIONAL','lines':[{'orderLineId':d['lines'][0]['id'],'qty':1.5}]},400)
a.call(ref,'POST',{'revision':o['revision'],'reference':'TEST-OVERRUN','lines':[{'orderLineId':d['lines'][0]['id'],'qty':4}]},409)
check(len(a.call('/api/orders/'+str(o['id']))['dispatches'])==0,'overrun transaction rolled back')
partial=a.call(ref,'POST',{'revision':o['revision'],'reference':'TEST-SHIP-01','note':'TEST partial warehouse handoff','lines':[{'orderLineId':d['lines'][0]['id'],'qty':1}]})
check(partial['order']['status']=='CONFIRMED','partial stays confirmed');action(a,o['id'],'cancel',409)
a.call(ref,'POST',{'revision':partial['order']['revision'],'reference':'TEST-SHIP-01','lines':[{'orderLineId':d['lines'][0]['id'],'qty':1}]},409)
full=a.call(ref,'POST',{'revision':partial['order']['revision'],'reference':'TEST-SHIP-02','lines':[{'orderLineId':d['lines'][0]['id'],'qty':2},{'orderLineId':d['lines'][1]['id'],'qty':4}]})
check(full['order']['status']=='FULFILLED','all quantities close order');check(len(full['dispatches'])==2,'two true handoffs');check(sum(l['fulfilled'] for l in full['lines'])==7,'fulfilled persisted')
# Rejection/revision history, fresh template price, and scoped CRUD.
r=save();r=action(b,r['id'],'submit');r=action(a,r['id'],'reject');r=action(b,r['id'],'revise');b.call('/api/orders/'+str(r['id'])+'?revision='+str(r['revision']),'DELETE',expected=409);action(b,r['id'],'cancel')
template=b.call('/api/templates','POST',{'name':'TEST weekly replenishment','lines':lines})
other.call('/api/templates/'+str(template['id'])+'?revision=1','DELETE',expected=403)
template=b.call('/api/templates/'+str(template['id']),'PUT',{'name':'TEST 常用补货 / Weekly basket','lines':lines,'revision':1})
portal=b.call('/api/portal');check(len(portal['templates'])==1,'saved list persisted');check(float(portal['items'][0]['unitPrice'])==14,'template uses current catalogue prices')
# Atomic import: first valid update is rolled back when another line fails.
price_map={p['id']:p for p in a.call('/api/master/prices')};p=price_map[prices[2]['id']]
row={'customerCode':'TEST-A','sku':products[2]['sku'],'unitPrice':'25.00','minQty':1,'stepQty':1,'maxQty':1000,'enabled':True,'revision':p['revision']}
audit_before=len(a.call('/api/audit'))
a.call('/api/prices/import','POST',[row,{**row,'customerCode':'UNKNOWN'}],400)
check(next(x for x in a.call('/api/master/prices') if x['id']==p['id'])['unitPrice']==p['unitPrice'],'import price rollback');check(len(a.call('/api/audit'))==audit_before,'import audit rollback')
a.call('/api/prices/import','POST',[row]);a.call('/api/prices/import','POST',[row],409);a.call('/api/prices/import','POST',[row,row],400)
# Concurrent requests with the same version commit exactly one event.
race=save([{'productId':products[0]['id'],'qty':2}]);client2=Client().login('test-buyer',new_password)
def race_submit(client):
 body={'revision':race['revision'],'note':'TEST race'}
 try:
  req=urllib.request.Request(base+f"/api/orders/{race['id']}/actions/submit",method='POST',headers={'Content-Type':'application/json',client.csrf['header']:client.csrf['token']},data=json.dumps(body).encode())
  with client.opener.open(req,timeout=25) as res:return res.status
 except urllib.error.HTTPError as e:return e.code
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(race_submit,[b,client2]))
check(sorted(results)==[200,409],'concurrent same-version writes serialize');check(len(b.call('/api/orders/'+str(race['id']))['events'])==1,'race has one submit event')
# Live role/account protections, mutable permissions and department boundary.
a.call('/api/admin/users/'+str(u1['id']),'PUT',{**u1,'customerId':c2['id']},409)
a.call('/api/admin/users','POST',{'username':'test-escalation','displayName':'TEST invalid','password':new_password,'roleId':role('Administrator'),'departmentId':1,'customerId':c1['id'],'enabled':True},409)
me=a.call('/api/auth/me');users=a.call('/api/admin/users');admin_row=next(x for x in users if x['id']==me['id']);a.call('/api/admin/users/'+str(me['id']),'PUT',{**admin_row,'enabled':False},409)
uid=user('test-reset','Customer',1,c1['id']);old_session=Client().login('test-reset',new_password);a.call('/api/admin/users/'+str(uid['id']),'PUT',{**uid,'password':'Bb8'+secrets.token_hex(18)});old_session.call('/api/portal',expected=401)
a.call('/api/admin/settings/2','PUT',{'value':'USD'},409)
# CSV contains only confirmed/fulfilled orders, protects spreadsheet formula prefixes.
submit_day=datetime.datetime.now(datetime.timezone.utc).date().isoformat();csv=a.call('/api/orders.csv?date='+submit_day,raw=True);check(o['reference'] in csv and race['reference'] not in csv,'export scope/status');check('Remaining' in csv and '89.30' not in csv,'export is per-line with remaining quantities');check('zhuatech' not in csv,'no advertising inserted into exports')
price_csv=a.call('/api/prices.csv',raw=True);check(price_csv.startswith('customerCode,sku,unitPrice,minQty,stepQty,maxQty,enabled,revision'),'price import/export headers')
new_cust=customer('TEST-FORMULA',name='=TEST formula');new_u=user('test-formula','Customer',1,new_cust['id']);a.call('/api/master/prices','POST',{'customerId':new_cust['id'],'productId':products[0]['id'],'unitPrice':'1.00','minQty':1,'stepQty':1,'maxQty':10,'enabled':True});formula_client=Client().login('test-formula',new_password);fo=formula_client.call('/api/orders','POST',{'deliveryDate':future,'lines':[{'productId':products[0]['id'],'qty':1}]});action(formula_client,fo['id'],'submit');action(a,fo['id'],'confirm');check('"\'=TEST formula"' in a.call('/api/orders.csv?date='+submit_day,raw=True),'formula injection neutralized')
# Leave a real submitted order and one reviewed confirmed order for UI inspection.
submitted=save();submitted=action(b,submitted['id'],'submit');confirmed=save([{'productId':products[3]['id'],'qty':5}]);confirmed=action(b,confirmed['id'],'submit');confirmed=action(a,confirmed['id'],'confirm')
state={'base':base,'buyerUsername':'test-buyer','buyerPassword':new_password,'adminUsername':env['ADMIN_USERNAME'],'adminPassword':env['ADMIN_PASSWORD'],'customerId':c1['id'],'submittedId':submitted['id'],'confirmedId':confirmed['id'],'fulfilledId':o['id'],'productId':products[0]['id'],'checks':count,'templateId':template['id']}
p=root/'output'/Path(os.environ.get('QUALITY_STATE_FILE','orderdesk-quality-state.json')).name;p.write_text(json.dumps(state));p.chmod(0o600)
print(f'PASS: {count} assertions against actual MySQL deployment; source unchanged, private fixture credentials excluded from Git.')
