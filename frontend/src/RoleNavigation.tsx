import React,{useMemo,useState} from 'react';
import BrandLogo from './BrandLogo';

export type NavigationItem={key:string;label:string;permission?:string|string[];allPermissions?:string[];requiredRoles?:string[]};
export type NavigationGroup={label:string;items:NavigationItem[];singleLink?:boolean};
const roleMenus:Record<string,NavigationGroup[]>={
 platform:[
  {label:'Dashboard',singleLink:true,items:[{key:'dashboard',label:'Dashboard'}]},
  {label:'Subscriptions',items:[{key:'subscriptions',label:'Plans',permission:'SUBSCRIPTION_MANAGE'},{key:'assignments',label:'Assignments',permission:'SUBSCRIPTION_MANAGE'}]},
  {label:'Analytics',singleLink:true,items:[{key:'analytics',label:'Analytics',permission:'SUBSCRIPTION_MANAGE'}]},
  {label:'Audit',singleLink:true,items:[{key:'audit',label:'Audit',permission:'AUDIT_VIEW'}]},
  {label:'Tenants',singleLink:true,items:[{key:'tenants',label:'Tenants',permission:'PLATFORM_MANAGE'}]},
  {label:'Settings',singleLink:true,items:[{key:'settings',label:'Settings',permission:'PLATFORM_MANAGE'}]}
 ],
 vendor:[
  {label:'Dashboard',singleLink:true,items:[{key:'dashboard',label:'Dashboard'}]},
  {label:'Catalog',items:[{key:'categories',label:'Categories',permission:['PRODUCT_VIEW','PRODUCT_CREATE']},{key:'products',label:'Products',permission:['PRODUCT_VIEW','PRODUCT_CREATE']},{key:'packages',label:'Solar Packages',permission:['PACKAGE_VIEW','PACKAGE_CREATE']}]},
  {label:'Sales',items:[{key:'enquiries',label:'Enquiries',permission:['LEAD_VIEW','LEAD_CREATE']},{key:'leads',label:'Leads',permission:['LEAD_VIEW','LEAD_CREATE']},{key:'surveys',label:'Site Surveys',permission:['SURVEY_VIEW','SURVEY_CREATE']},{key:'quotations',label:'Quotations',permission:['QUOTATION_VIEW','QUOTATION_CREATE']},{key:'orders',label:'Orders',permission:['ORDER_VIEW','ORDER_CREATE']}]},
  {label:'Customers & Team',items:[{key:'customers',label:'Customers',permission:['CUSTOMER_VIEW','CUSTOMER_CREATE']},{key:'users',label:'Users',permission:'USER_VIEW'},{key:'roles',label:'Roles & Permissions',permission:'ROLE_VIEW'},{key:'branches',label:'Branches',permission:'BRANCH_MANAGE'}]},
  {label:'Insights',items:[{key:'analytics',label:'Analytics',requiredRoles:['VENDOR_ADMIN']}]},
  {label:'Business Operations',items:[{key:'operations',label:'Inventory & Operations',permission:['INVENTORY_VIEW','INSTALLATION_VIEW']},{key:'finance-service',label:'Finance & Service',permission:['INVOICE_VIEW','SERVICE_VIEW']},{key:'offline-sync',label:'Offline Business Sync'}]},
  {label:'Account',items:[{key:'subscription',label:'My Subscription',requiredRoles:['VENDOR_ADMIN']},{key:'settings',label:'Settings',permission:'QUOTATION_APPROVE_DISCOUNT',requiredRoles:['VENDOR_ADMIN']}]}
 ],
 customer:[
  {label:'Dashboard',singleLink:true,items:[{key:'dashboard',label:'Dashboard'}]},
  {label:'My Account',items:[{key:'profile',label:'My Profile'},{key:'customer-orders',label:'My Orders'},{key:'invoices',label:'My Invoices'},{key:'quotations',label:'My Quotation'},{key:'enquiries',label:'My Enquiries'},{key:'addresses',label:'Addresses'},{key:'settings',label:'Settings'},{key:'logout',label:'Logout'}]}
 ]
};
export function getRoleNavigation(role:'platform'|'vendor'|'customer',permissions:string[]=[],roles:string[]=[]):NavigationGroup[]{
 return roleMenus[role].map(group=>({...group,items:group.items.filter(item=>(!item.permission||(Array.isArray(item.permission)?item.permission.some(p=>permissions.includes(p)):permissions.includes(item.permission)))&&(!item.allPermissions||item.allPermissions.every(p=>permissions.includes(p)))&&(!item.requiredRoles||item.requiredRoles.some(r=>roles.includes(r))))})).filter(group=>group.items.length>0);
}
export function getNavigationKeys(role:'platform'|'vendor'|'customer',permissions:string[]=[],roles:string[]=[]):string[]{
 return getRoleNavigation(role,permissions,roles).flatMap(group=>group.items.map(item=>item.key));
}
type Props={role:'platform'|'vendor'|'customer';active:string;onNavigate:(key:string)=>void;children:React.ReactNode;user:{name:string;accountType:string};onLogout:()=>void;planBadge?:string;breadcrumbs?:string[];permissions?:string[];roles?:string[]};
export default function RoleShell({role,active,onNavigate,children,user,onLogout,planBadge,breadcrumbs=[],permissions=[],roles=[]}:Props){
 const groups=useMemo(()=>getRoleNavigation(role,permissions,roles),[role,permissions.join('|'),roles.join('|')]);
 const [mobileOpen,setMobileOpen]=useState(false);
 const [collapsed,setCollapsed]=useState<Record<string,boolean>>({});
 const activeGroup=groups.find(g=>g.items.some(i=>i.key===active))?.label;
 const select=(key:string)=>{if(key==='logout')onLogout();else onNavigate(key);setMobileOpen(false)};
 const activeLabel=groups.flatMap(g=>g.items).find(i=>i.key===active)?.label||active;
 const dashboard=()=>onNavigate('dashboard');
 return <div className={'role-app role-'+role}>
  {mobileOpen&&<button type="button" className="role-drawer-scrim" aria-label="Close menu" onClick={()=>setMobileOpen(false)}/>}
  <aside className={'role-sidebar'+(mobileOpen?' is-open':'')} aria-label="Application navigation">
   <div className="role-sidebar-brand">{role!=='customer'&&<BrandLogo className="role-sidebar-logo" onClick={dashboard}/>}<span><b>Saumya Solar</b><small>{role==='platform'?'Platform Console':role==='customer'?'Customer Account':'Vendor Workspace'}</small></span><button type="button" className="role-sidebar-close" aria-label="Close menu" onClick={()=>setMobileOpen(false)}>×</button></div>
   <nav className="role-menu">{groups.map(group=>{const expanded=group.label===activeGroup||!(collapsed[group.label]??true);return <section className="role-menu-group" key={group.label}>{group.singleLink?<button type="button" className={'role-menu-item role-menu-single'+(active===group.items[0].key?' active':'')} aria-current={active===group.items[0].key?'page':undefined} onClick={()=>select(group.items[0].key)}>{group.items[0].label}</button>:<><button type="button" className="role-menu-group-title" aria-expanded={expanded} onClick={()=>{if(group.label!==activeGroup)setCollapsed(v=>({...v,[group.label]:expanded}))}}>{group.label}<span>{expanded?'−':'＋'}</span></button>{expanded&&<div className="role-menu-items">{group.items.map(item=><button type="button" key={item.key} className={'role-menu-item'+(active===item.key?' active':'')} aria-current={active===item.key?'page':undefined} onClick={()=>select(item.key)}>{item.label}</button>)}</div>}</>}</section>})}</nav>
   <div className="role-sidebar-footer"><span className="role-avatar">{(user.name||'U').slice(0,1).toUpperCase()}</span><span><b>{user.name}</b><small>{user.accountType.replaceAll('_',' ')}</small></span></div>
  </aside>
  <div className="role-main-column"><header className="role-topbar">{role!=='customer'&&<BrandLogo className="role-topbar-logo" onClick={dashboard}/>}<button type="button" className="role-mobile-toggle" aria-label="Open navigation" aria-expanded={mobileOpen} onClick={()=>setMobileOpen(true)}>☰</button><div className="role-breadcrumbs"><span>Workspace</span><span>/</span>{breadcrumbs.length?breadcrumbs.map((x,i)=><React.Fragment key={x}>{i>0&&<span>/</span>}<b>{x}</b></React.Fragment>):<b>{activeLabel}</b>}</div><div className="role-topbar-actions">{planBadge&&<span className="role-plan-badge">{planBadge}</span>}{role!=='customer'&&<><span className="role-topbar-user">{user.name}</span><button type="button" className="role-logout" onClick={onLogout}>Logout</button></>}</div></header><div className="role-content">{children}</div></div>
 </div>;
}
