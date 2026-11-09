import React,{useMemo,useState} from 'react';

export type NavigationItem={key:string;label:string;permission?:string|string[]};
export type NavigationGroup={label:string;items:NavigationItem[]};
const roleMenus:Record<string,NavigationGroup[]>={
 platform:[
  {label:'Workspace',items:[{key:'dashboard',label:'Dashboard'},{key:'tenants',label:'Tenants'}]},
  {label:'Subscriptions',items:[{key:'subscriptions',label:'Subscription Plans & Assignments'}]},
  {label:'Insights',items:[{key:'analytics',label:'Analytics'}]}
 ],
 vendor:[
  {label:'Overview',items:[{key:'dashboard',label:'Dashboard'}]},
  {label:'Catalog',items:[{key:'categories',label:'Categories',permission:'CATALOG_MANAGE'},{key:'products',label:'Products',permission:'CATALOG_MANAGE'},{key:'packages',label:'Solar Packages',permission:'CATALOG_MANAGE'}]},
  {label:'Sales',items:[{key:'enquiries',label:'Enquiries',permission:'LEAD_MANAGE'},{key:'leads',label:'Leads',permission:'LEAD_MANAGE'},{key:'surveys',label:'Site Surveys',permission:'SURVEY_MANAGE'},{key:'quotations',label:'Quotations',permission:'QUOTATION_MANAGE'},{key:'orders',label:'Orders',permission:'ORDER_CREATE'}]},
  {label:'Customers & Team',items:[{key:'customers',label:'Customers',permission:'CUSTOMER_MANAGE'},{key:'users',label:'Users',permission:'USER_VIEW'},{key:'branches',label:'Branches',permission:'BRANCH_MANAGE'}]},
  {label:'Insights',items:[{key:'analytics',label:'Analytics',permission:'BRANCH_MANAGE'}]},
  {label:'Business operations',items:[{key:'operations',label:'Inventory & Operations',permission:'INVENTORY_MANAGE'},{key:'finance-service',label:'Finance & Service',permission:'FINANCE_MANAGE'},{key:'offline-sync',label:'Offline Business Sync'}]},
  {label:'Account',items:[{key:'subscription',label:'My Subscription',permission:['SUBSCRIPTION_VIEW','SUBSCRIPTION_MANAGE']},{key:'settings',label:'Settings',permission:'DISCOUNT_POLICY_MANAGE'}]}
 ],
 customer:[
  {label:'My Account',items:[{key:'dashboard',label:'Account Overview'},{key:'catalog',label:'Our Products'},{key:'request',label:'Request a Quotation'},{key:'enquiries',label:'My Enquiries'},{key:'quotations',label:'My Quotations'},{key:'customer-orders',label:'My Orders'},{key:'invoices',label:'My Invoices'},{key:'payments',label:'My Payments'},{key:'my-installations',label:'My Installations'},{key:'warranties',label:'My Warranties'},{key:'warranty-claims',label:'Warranty Claims'},{key:'service',label:'Service Requests'},{key:'notifications',label:'Notification Preferences'}]}
 ]
};
export function getRoleNavigation(role:'platform'|'vendor'|'customer',permissions:string[]=[]):NavigationGroup[]{
 return roleMenus[role].map(group=>({...group,items:group.items.filter(item=>!item.permission||(Array.isArray(item.permission)?item.permission.some(p=>permissions.includes(p)):permissions.includes(item.permission)))})).filter(group=>group.items.length>0);
}
export function getNavigationKeys(role:'platform'|'vendor'|'customer',permissions:string[]=[]):string[]{
 return getRoleNavigation(role,permissions).flatMap(group=>group.items.map(item=>item.key));
}
type Props={role:'platform'|'vendor'|'customer';active:string;onNavigate:(key:string)=>void;children:React.ReactNode;user:{name:string;accountType:string};onLogout:()=>void;planBadge?:string;breadcrumbs?:string[];permissions?:string[]};
export default function RoleShell({role,active,onNavigate,children,user,onLogout,planBadge,breadcrumbs=[],permissions=[]}:Props){
 const groups=useMemo(()=>getRoleNavigation(role,permissions),[role,permissions.join('|')]);
 const [mobileOpen,setMobileOpen]=useState(false);
 const [collapsed,setCollapsed]=useState<Record<string,boolean>>({});
 const activeGroup=groups.find(g=>g.items.some(i=>i.key===active))?.label;
 const select=(key:string)=>{onNavigate(key);setMobileOpen(false)};
 const activeLabel=groups.flatMap(g=>g.items).find(i=>i.key===active)?.label||active;
 return <div className={'role-app role-'+role}>
  {mobileOpen&&<button type="button" className="role-drawer-scrim" aria-label="Close menu" onClick={()=>setMobileOpen(false)}/>}
  <aside className={'role-sidebar'+(mobileOpen?' is-open':'')} aria-label="Application navigation">
   <div className="role-sidebar-brand"><span className="role-brand-mark">S</span><span><b>Saumya Solar</b><small>{role==='platform'?'Platform Console':role==='customer'?'Customer Account':'Vendor Workspace'}</small></span><button type="button" className="role-sidebar-close" aria-label="Close menu" onClick={()=>setMobileOpen(false)}>×</button></div>
   <nav className="role-menu">{groups.map(group=>{const expanded=group.label===activeGroup||!(collapsed[group.label]??true);return <section className="role-menu-group" key={group.label}><button type="button" className="role-menu-group-title" aria-expanded={expanded} onClick={()=>{if(group.label!==activeGroup)setCollapsed(v=>({...v,[group.label]:expanded}))}}>{group.label}<span>{expanded?'−':'＋'}</span></button>{expanded&&<div className="role-menu-items">{group.items.map(item=><button type="button" key={item.key} className={'role-menu-item'+(active===item.key?' active':'')} aria-current={active===item.key?'page':undefined} onClick={()=>select(item.key)}>{item.label}</button>)}</div>}</section>})}</nav>
   <div className="role-sidebar-footer"><span className="role-avatar">{(user.name||'U').slice(0,1).toUpperCase()}</span><span><b>{user.name}</b><small>{user.accountType.replaceAll('_',' ')}</small></span></div>
  </aside>
  <div className="role-main-column"><header className="role-topbar"><button type="button" className="role-mobile-toggle" aria-label="Open navigation" aria-expanded={mobileOpen} onClick={()=>setMobileOpen(true)}>☰</button><div className="role-breadcrumbs"><span>Workspace</span><span>/</span>{breadcrumbs.length?breadcrumbs.map((x,i)=><React.Fragment key={x}>{i>0&&<span>/</span>}<b>{x}</b></React.Fragment>):<b>{activeLabel}</b>}</div><div className="role-topbar-actions">{planBadge&&<span className="role-plan-badge">{planBadge}</span>}{role!=='customer'&&<><span className="role-topbar-user">{user.name}</span><button type="button" className="role-logout" onClick={onLogout}>Logout</button></>}</div></header><div className="role-content">{children}</div></div>
 </div>;
}
